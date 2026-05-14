package io.github.karunarathnad.webhook.delivery;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;

public interface WebhookDeliveryListener {

    default void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {}

    default void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {}
}