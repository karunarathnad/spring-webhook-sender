package io.github.karunarathnad.webhook.async;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * Submits webhook deliveries to a dedicated {@link ThreadPoolTaskExecutor} so callers of
 * {@link io.github.karunarathnad.webhook.core.WebhookClient#sendAsync} never block on
 * network I/O.
 *
 * <p>If the executor's queue is full, the event is dropped without an HTTP call: rather
 * than propagating a {@link RejectedExecutionException}, {@link #dispatch} resolves to a
 * failure {@link WebhookDeliveryResult} and notifies the {@link WebhookDeliveryListener},
 * consistent with the library's contract that dispatch methods never throw.
 */
public class AsyncWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncWebhookDispatcher.class);

    private final ThreadPoolTaskExecutor executor;
    private final WebhookHttpSender httpSender;
    private final WebhookDeliveryListener deliveryListener;

    public AsyncWebhookDispatcher(ThreadPoolTaskExecutor executor, WebhookHttpSender httpSender,
                                   WebhookDeliveryListener deliveryListener) {
        this.executor = executor;
        this.httpSender = httpSender;
        this.deliveryListener = deliveryListener;
    }

    public CompletableFuture<WebhookDeliveryResult> dispatch(WebhookEvent event, WebhookEndpoint endpoint) {
        log.debug("Dispatching webhook eventId={} eventType={} endpointId={}",
                event.eventId(), event.eventType(), endpoint.id());
        try {
            return CompletableFuture.supplyAsync(
                    () -> httpSender.send(event, endpoint),
                    executor);
        } catch (RejectedExecutionException e) {
            log.error("Webhook queue full — event dropped eventId={} endpointId={}",
                    event.eventId(), endpoint.id());
            // WebhookClient#send/#sendAsync are documented to never throw — always resolve
            // to a WebhookDeliveryResult, even when the event never made it past the queue.
            WebhookDeliveryResult result = WebhookDeliveryResult.failure(
                    event.eventId(), endpoint.id(), -1,
                    "Webhook queue full — event dropped", 0, Duration.ZERO);
            try {
                deliveryListener.onPermanentFailure(event, endpoint, result);
            } catch (Exception listenerEx) {
                log.warn("webhookDeliveryListener callback threw an exception", listenerEx);
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}