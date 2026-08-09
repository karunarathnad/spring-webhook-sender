package io.github.karunarathnad.webhook.async;

import io.github.karunarathnad.webhook.config.WebhookProperties;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.delivery.LoggingWebhookDeliveryListener;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import io.github.karunarathnad.webhook.signature.HmacSha256SignatureStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a bug where a full async queue made {@code dispatch()} return a
 * {@code CompletableFuture.failedFuture(RejectedExecutionException)}. Since
 * {@link io.github.karunarathnad.webhook.core.DefaultWebhookClient#send} calls
 * {@code .join()} on that future, the exception propagated as an uncaught
 * {@code CompletionException} — violating {@code WebhookClient}'s documented contract
 * that {@code send}/{@code sendAsync} never throw and always resolve to a
 * {@link WebhookDeliveryResult}.
 */
class AsyncWebhookDispatcherQueueFullTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void queueFullResolvesToAGracefulFailureInsteadOfThrowing() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/hooks/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        WebhookProperties properties = new WebhookProperties();
        WebhookHttpSender httpSender = new WebhookHttpSender(
                RestClient.builder()
                        .requestFactory(new HttpComponentsClientHttpRequestFactory())
                        .build(),
                new HmacSha256SignatureStrategy(),
                new ObjectMapper(),
                record -> { },
                new LoggingWebhookDeliveryListener(),
                properties);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();

        AsyncWebhookDispatcher dispatcher =
                new AsyncWebhookDispatcher(executor, httpSender, new LoggingWebhookDeliveryListener());

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("slow-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/slow")
                .build();
        WebhookEvent event = WebhookEvent.builder().eventType("test.event").payload(Map.of()).build();

        try {
            // Occupies the only worker thread for ~500ms.
            CompletableFuture<WebhookDeliveryResult> first = dispatcher.dispatch(event, endpoint);
            Thread.sleep(100); // let it actually start running, not just get submitted

            // Pool is busy and the queue has zero capacity, so this must be rejected.
            CompletableFuture<WebhookDeliveryResult> second = dispatcher.dispatch(event, endpoint);
            WebhookDeliveryResult secondResult = second.get(2, TimeUnit.SECONDS);

            assertThat(secondResult.success()).isFalse();
            assertThat(secondResult.totalAttempts()).isZero();
            assertThat(secondResult.errorMessage()).containsIgnoringCase("queue full");

            first.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }
}
