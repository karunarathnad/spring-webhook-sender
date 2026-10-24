package io.github.karunarathnad.webhook.async;

import io.github.karunarathnad.webhook.config.WebhookProperties;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.delivery.LoggingWebhookDeliveryListener;
import io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import io.github.karunarathnad.webhook.signature.HmacSha256SignatureStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for {@link AsyncWebhookDispatcher} covering the normal dispatch path,
 * {@link AsyncWebhookDispatcher#shutdown()}, and the queue-full branch where the
 * {@link WebhookDeliveryListener} callback itself throws. The pure-rejection happy path
 * for queue-full is already covered by {@link AsyncWebhookDispatcherQueueFullTest}.
 */
class AsyncWebhookDispatcherTest {

    static WireMockServer wireMock;

    private ThreadPoolTaskExecutor executor;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void dispatchSubmitsToExecutorAndResolvesWithTheHttpSenderResult() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(200)));

        executor = newExecutor();
        WebhookHttpSender httpSender = newHttpSender(new LoggingWebhookDeliveryListener());
        AsyncWebhookDispatcher dispatcher =
                new AsyncWebhookDispatcher(executor, httpSender, new LoggingWebhookDeliveryListener());

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("billing-service")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .build();
        WebhookEvent event = WebhookEvent.builder().eventType("order.created").payload(Map.of()).build();

        WebhookDeliveryResult result = dispatcher.dispatch(event, endpoint).get(2, TimeUnit.SECONDS);

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        wireMock.verify(postRequestedFor(urlEqualTo("/hooks")));
    }

    @Test
    void shutdownStopsTheExecutor() {
        executor = newExecutor();
        WebhookHttpSender httpSender = newHttpSender(new LoggingWebhookDeliveryListener());
        AsyncWebhookDispatcher dispatcher =
                new AsyncWebhookDispatcher(executor, httpSender, new LoggingWebhookDeliveryListener());

        dispatcher.shutdown();

        assertThat(executor.getThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Test
    void queueFullStillResolvesGracefullyWhenTheListenerCallbackItselfThrows() throws Exception {
        executor = newExecutor();

        CountDownLatch occupierStarted = new CountDownLatch(1);
        CountDownLatch releaseOccupier = new CountDownLatch(1);
        executor.execute(() -> {
            occupierStarted.countDown();
            try {
                releaseOccupier.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(occupierStarted.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean listenerInvoked = new AtomicBoolean(false);
        AtomicReference<WebhookDeliveryResult> listenerResult = new AtomicReference<>();
        WebhookDeliveryListener throwingListener = new WebhookDeliveryListener() {
            @Override
            public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
                listenerInvoked.set(true);
                listenerResult.set(result);
                throw new IllegalStateException("boom — simulated bug in a custom listener");
            }
        };

        WebhookHttpSender httpSender = newHttpSender(new LoggingWebhookDeliveryListener());
        AsyncWebhookDispatcher dispatcher = new AsyncWebhookDispatcher(executor, httpSender, throwingListener);
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("queue-full-endpoint")
                .targetUrl("http://localhost:1/unused")
                .build();
        WebhookEvent event = WebhookEvent.builder().eventType("test.event").payload(Map.of()).build();

        try {
            // If the listener's exception escaped dispatch() uncaught, this .get() would
            // throw and fail the test before the assertions below even run.
            CompletableFuture<WebhookDeliveryResult> dispatched = dispatcher.dispatch(event, endpoint);
            WebhookDeliveryResult result = dispatched.get(2, TimeUnit.SECONDS);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).containsIgnoringCase("queue full");
            assertThat(listenerInvoked).isTrue();
            assertThat(listenerResult.get()).isEqualTo(result);
        } finally {
            releaseOccupier.countDown();
        }
    }

    private static ThreadPoolTaskExecutor newExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }

    private static WebhookHttpSender newHttpSender(WebhookDeliveryListener deliveryListener) {
        return new WebhookHttpSender(
                RestClient.builder()
                        .requestFactory(new HttpComponentsClientHttpRequestFactory())
                        .build(),
                new HmacSha256SignatureStrategy(),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                record -> { },
                deliveryListener,
                new WebhookProperties());
    }
}
