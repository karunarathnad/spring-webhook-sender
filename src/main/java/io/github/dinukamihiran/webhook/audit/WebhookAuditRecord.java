package io.github.karunarathnad.webhook.audit;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;

import java.time.Instant;

public record WebhookAuditRecord(
        String eventId,
        String eventType,
        String endpointId,
        String targetUrl,
        int httpStatusCode,
        boolean success,
        String errorMessage,
        int attemptNumber,
        long durationMs,
        Instant timestamp
) {
    public static WebhookAuditRecord of(WebhookEvent event, WebhookEndpoint endpoint,
                                         int httpStatus, boolean success,
                                         String errorMessage, int attempt, long durationMs) {
        return new WebhookAuditRecord(
                event.eventId(),
                event.eventType(),
                endpoint.id(),
                endpoint.targetUrl(),
                httpStatus,
                success,
                errorMessage,
                attempt,
                durationMs,
                Instant.now()
        );
    }
}
