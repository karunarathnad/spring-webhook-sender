package io.github.karunarathnad.webhook.delivery;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link WebhookDeliveryListener} registered by the library's auto-configuration.
 *
 * <p>Successful deliveries are logged at INFO level and permanent failures at ERROR level,
 * both using the logger named after this class. The log lines include the event identifier,
 * event type, endpoint identifier, attempt count, and either the total duration or the
 * HTTP status and error message.
 *
 * <p>Register a custom {@link WebhookDeliveryListener} bean to replace this default with
 * application-specific behaviour such as sending alerts or writing to a dead-letter store.
 */
public class LoggingWebhookDeliveryListener implements WebhookDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebhookDeliveryListener.class);

    /**
     * Logs the successful delivery at INFO level.
     *
     * @param event    the event that was delivered
     * @param endpoint the endpoint that accepted the event
     * @param result   the delivery outcome
     */
    @Override
    public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        log.info("Webhook delivered eventId={} eventType={} endpointId={} attempts={} durationMs={}",
                event.eventId(), event.eventType(), endpoint.id(),
                result.totalAttempts(), result.totalDuration().toMillis());
    }

    /**
     * Logs the permanent failure at ERROR level.
     *
     * @param event    the event that could not be delivered
     * @param endpoint the endpoint that rejected or did not respond to the event
     * @param result   the delivery outcome, including the error reason
     */
    @Override
    public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        log.error("Webhook delivery permanently failed eventId={} eventType={} endpointId={} attempts={} httpStatus={} error={}",
                event.eventId(), event.eventType(), endpoint.id(),
                result.totalAttempts(), result.httpStatusCode(), result.errorMessage());
    }
}