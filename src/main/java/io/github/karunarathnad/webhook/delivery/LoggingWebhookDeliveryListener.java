package io.github.karunarathnad.webhook.delivery;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingWebhookDeliveryListener implements WebhookDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebhookDeliveryListener.class);

    @Override
    public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        log.info("Webhook delivered eventId={} eventType={} endpointId={} attempts={} durationMs={}",
                event.eventId(), event.eventType(), endpoint.id(),
                result.totalAttempts(), result.totalDuration().toMillis());
    }

    @Override
    public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        log.error("Webhook delivery permanently failed eventId={} eventType={} endpointId={} attempts={} httpStatus={} error={}",
                event.eventId(), event.eventType(), endpoint.id(),
                result.totalAttempts(), result.httpStatusCode(), result.errorMessage());
    }
}