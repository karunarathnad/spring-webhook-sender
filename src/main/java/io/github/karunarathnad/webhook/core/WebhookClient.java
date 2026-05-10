package io.github.karunarathnad.webhook.core;

import java.util.concurrent.CompletableFuture;

public interface WebhookClient {

    CompletableFuture<WebhookDeliveryResult> sendAsync(WebhookEvent event, WebhookEndpoint endpoint);

    WebhookDeliveryResult send(WebhookEvent event, WebhookEndpoint endpoint);
}