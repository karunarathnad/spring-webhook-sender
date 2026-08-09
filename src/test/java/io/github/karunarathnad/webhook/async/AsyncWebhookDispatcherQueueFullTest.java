package io.github.karunarathnad.webhook.async;

import io.github.karunarathnad.webhook.config.WebhookProperties;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.delivery.LoggingWebhookDeliveryListener;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import io.github.karunarathnad.webhook.signature.HmacSha256SignatureStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a bug where a full async queue made {@code dispatch()} return a
 * {@code CompletableFuture.failedFuture(RejectedExecutionException)}. Since
 * {@link io.github.karunarathnad.webhook.core.DefaultWebhookClient#send} calls
 * {@code .join()} on that future, the exception propagated as an uncaught
 * {@code CompletionException} — violating {@code WebhookClient}'s documented contract
 * that {@code send}/{@code sendAsync} never throw and always resolve to a
 * {@link WebhookDeliveryResult}.
 *
 * <p>The pool's only worker thread is occupied deterministically via a latch (not a
 * fixed sleep racing a real HTTP call), so the rejection is guaranteed rather than
 * timing-dependent. The rejected call never reaches HTTP at all, so no WireMock server
 * is needed here.
 */
class AsyncWebhookDispatcherQueueFullTest {

    @Test
    void queueFullResolvesToAGracefulFailureInsteadOfThrowing() throws Exception {
        WebhookHttpSender httpSender = new WebhookHttpSender(
                RestClient.builder()
                        .requestFactory(new HttpComponentsClientHttpRequestFactory())
                        .build(),
                new HmacSha256SignatureStrategy(),
                new ObjectMapper(),
                record -> { },
                new LoggingWebhookDeliveryListener(),
                new WebhookProperties());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();

        CountDownLatch occupierStarted = new CountDownLatch(1);
        CountDownLatch releaseOccupier = new CountDownLatch(1);

        // Deterministically occupy the pool's single thread, independent of any HTTP
        // timing: dispatcher.dispatch() below is only guaranteed to be rejected once we
        // know this task is actually running (not just submitted).
        executor.execute(() -> {
            occupierStarted.countDown();
            try {
                releaseOccupier.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(occupierStarted.await(2, TimeUnit.SECONDS))
                .as("occupier task should have started running")
                .isTrue();

        AsyncWebhookDispatcher dispatcher =
                new AsyncWebhookDispatcher(executor, httpSender, new LoggingWebhookDeliveryListener());
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("queue-full-endpoint")
                .targetUrl("http://localhost:1/unused")
                .build();
        WebhookEvent event = WebhookEvent.builder().eventType("test.event").payload(Map.of()).build();

        try {
            // The pool is provably busy and the queue has zero capacity, so this must
            // be rejected — never reaching httpSender.send() at all.
            CompletableFuture<WebhookDeliveryResult> rejected = dispatcher.dispatch(event, endpoint);
            WebhookDeliveryResult result = rejected.get(2, TimeUnit.SECONDS);

            assertThat(result.success()).isFalse();
            assertThat(result.totalAttempts()).isZero();
            assertThat(result.errorMessage()).containsIgnoringCase("queue full");
        } finally {
            releaseOccupier.countDown();
            executor.shutdown();
        }
    }
}
