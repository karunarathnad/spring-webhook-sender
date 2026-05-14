package io.github.karunarathnad.webhook.core;

import java.time.Duration;
import java.time.Instant;

public record WebhookDeliveryResult(
        String eventId,
        String endpointId,
        int httpStatusCode,
        boolean success,
        boolean skipped,
        String errorMessage,
        int totalAttempts,
        Duration totalDuration,
        Instant deliveredAt
) {
    public static WebhookDeliveryResult success(String eventId, String endpointId,
                                                 int httpStatus, int attempts, Duration duration) {
        return new WebhookDeliveryResult(
                eventId, endpointId, httpStatus, true, false, null, attempts, duration, Instant.now());
    }

    public static WebhookDeliveryResult failure(String eventId, String endpointId,
                                                 int httpStatus, String errorMessage,
                                                 int attempts, Duration duration) {
        return new WebhookDeliveryResult(
                eventId, endpointId, httpStatus, false, false, errorMessage, attempts, duration, Instant.now());
    }

    public static WebhookDeliveryResult skipped(String eventId, String endpointId, String reason) {
        return new WebhookDeliveryResult(
                eventId, endpointId, 0, false, true, reason, 0, Duration.ZERO, Instant.now());
    }
}