package io.github.karunarathnad.webhook.core;

import io.github.karunarathnad.webhook.async.AsyncWebhookDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DefaultWebhookClient implements WebhookClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebhookClient.class);

    private final AsyncWebhookDispatcher dispatcher;

    public DefaultWebhookClient(AsyncWebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<WebhookDeliveryResult> sendAsync(WebhookEvent event, WebhookEndpoint endpoint) {
        WebhookDeliveryResult skipped = checkSubscription(event, endpoint);
        if (skipped != null) return CompletableFuture.completedFuture(skipped);
        return dispatcher.dispatch(event, endpoint);
    }

    @Override
    public WebhookDeliveryResult send(WebhookEvent event, WebhookEndpoint endpoint) {
        WebhookDeliveryResult skipped = checkSubscription(event, endpoint);
        if (skipped != null) return skipped;
        return dispatcher.dispatch(event, endpoint).join();
    }

    private WebhookDeliveryResult checkSubscription(WebhookEvent event, WebhookEndpoint endpoint) {
        Set<String> subscribed = endpoint.subscribedEventTypes();
        if (!subscribed.isEmpty() && !subscribed.contains(event.eventType())) {
            String reason = "Event type '" + event.eventType() + "' not subscribed at endpoint '" + endpoint.id() + "'";
            log.debug("Skipping webhook dispatch — {}", reason);
            return WebhookDeliveryResult.skipped(event.eventId(), endpoint.id(), reason);
        }
        return null;
    }
}