package io.github.karunarathnad.webhook.core;

import java.time.Duration;
import java.time.Instant;

public record WebhookDeliveryResult(
        String eventId,
        String endpointId,
        int httpStatusCode,
        boolean success,
        String errorMessage,
        int totalAttempts,
        Duration totalDuration,
        Instant deliveredAt
) {
    public static WebhookDeliveryResult success(String eventId, String endpointId,
                                                 int httpStatus, int attempts, Duration duration) {
        return new WebhookDeliveryResult(
                eventId, endpointId, httpStatus, true, null, attempts, duration, Instant.now());
    }

    public static WebhookDeliveryResult failure(String eventId, String endpointId,
                                                 int httpStatus, String errorMessage,
                                                 int attempts, Duration duration) {
        return new WebhookDeliveryResult(
                eventId, endpointId, httpStatus, false, errorMessage, attempts, duration, Instant.now());
    }
}
