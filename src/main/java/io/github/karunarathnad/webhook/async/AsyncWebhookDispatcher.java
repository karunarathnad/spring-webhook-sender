package io.github.karunarathnad.webhook.async;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

public class AsyncWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncWebhookDispatcher.class);

    private final ThreadPoolTaskExecutor executor;
    private final WebhookHttpSender httpSender;

    public AsyncWebhookDispatcher(ThreadPoolTaskExecutor executor, WebhookHttpSender httpSender) {
        this.executor = executor;
        this.httpSender = httpSender;
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
            return CompletableFuture.failedFuture(e);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}