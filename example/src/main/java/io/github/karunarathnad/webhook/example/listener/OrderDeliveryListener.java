package io.github.karunarathnad.webhook.example.listener;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Custom delivery listener — replace the default LoggingWebhookDeliveryListener
 * by implementing WebhookDeliveryListener and registering it as a Spring bean.
 *
 * In a real application you would persist delivery records to a database,
 * emit metrics to Micrometer, or send alerts via PagerDuty from here.
 */
@Component
public class OrderDeliveryListener implements WebhookDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveryListener.class);

    @Override
    public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        log.info("[DELIVERED] event={} endpoint={} attempts={} durationMs={}",
                event.eventType(),
                endpoint.id(),
                result.totalAttempts(),
                result.totalDuration().toMillis());
    }

    @Override
    public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        log.error("[FAILED] event={} endpoint={} error={} attempts={}",
                event.eventType(),
                endpoint.id(),
                result.errorMessage(),
                result.totalAttempts());
        // Production todo: persist to a dead-letter store and alert on-call.
    }
}
