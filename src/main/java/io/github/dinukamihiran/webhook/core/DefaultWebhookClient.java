package io.github.karunarathnad.webhook.core;

import io.github.karunarathnad.webhook.async.AsyncWebhookDispatcher;

import java.util.concurrent.CompletableFuture;

public class DefaultWebhookClient implements WebhookClient {

    private final AsyncWebhookDispatcher dispatcher;

    public DefaultWebhookClient(AsyncWebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<WebhookDeliveryResult> sendAsync(WebhookEvent event, WebhookEndpoint endpoint) {
        return dispatcher.dispatch(event, endpoint);
    }

    @Override
    public WebhookDeliveryResult send(WebhookEvent event, WebhookEndpoint endpoint) {
        return dispatcher.dispatch(event, endpoint).join();
    }
}