package io.github.karunarathnad.webhook.core;

import java.time.Duration;
import java.time.Instant;

/**
 * An immutable record of how a single webhook delivery attempt concluded.
 *
 * <p>A result is returned by {@link WebhookClient#send} and by the future returned from
 * {@link WebhookClient#sendAsync} once all retry attempts have finished. It carries
 * enough information to decide whether to re-queue the event, raise an alert, or simply
 * log the outcome.
 *
 * <p>Three factory methods cover the distinct delivery outcomes:
 * <ul>
 *   <li>{@link #success} when the endpoint accepted the request with a 2xx response.</li>
 *   <li>{@link #failure} when all retry attempts were exhausted without a successful response.</li>
 *   <li>{@link #skipped} when the event type is not in the endpoint's subscription list.</li>
 * </ul>
 */
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

    /**
     * Creates a result representing a successful delivery.
     *
     * @param eventId    the identifier of the delivered event
     * @param endpointId the identifier of the endpoint that accepted the event
     * @param httpStatus the HTTP status code returned by the endpoint (typically 200 or 202)
     * @param attempts   the total number of attempts made, including the first try
     * @param duration   the total time spent across all attempts
     * @return a successful delivery result
     */
    public static WebhookDeliveryResult success(String eventId, String endpointId,
                                                 int httpStatus, int attempts, Duration duration) {
        return new WebhookDeliveryResult(
                eventId, endpointId, httpStatus, true, false, null, attempts, duration, Instant.now());
    }

    /**
     * Creates a result representing a permanent delivery failure after all retries were exhausted.
     *
     * @param eventId      the identifier of the event that could not be delivered
     * @param endpointId   the identifier of the target endpoint
     * @param httpStatus   the last HTTP status code received, or {@code -1} for network errors
     * @param errorMessage a human-readable description of the failure reason
     * @param attempts     the total number of attempts made
     * @param duration     the total time spent across all attempts
     * @return a failure result
     */
    public static WebhookDeliveryResult failure(String eventId, String endpointId,
                                                 int httpStatus, String errorMessage,
                                                 int attempts, Duration duration) {
        return new WebhookDeliveryResult(
                eventId, endpointId, httpStatus, false, false, errorMessage, attempts, duration, Instant.now());
    }

    /**
     * Creates a result representing an event that was intentionally not delivered because
     * its type is not in the endpoint's subscription list.
     *
     * <p>A skipped result is not a failure. No HTTP request was made and no retry will
     * be attempted. Callers can detect this case with {@link #skipped()}.
     *
     * @param eventId    the identifier of the skipped event
     * @param endpointId the identifier of the endpoint that does not subscribe to the event type
     * @param reason     a human-readable explanation of why the event was skipped
     * @return a skipped result
     */
    public static WebhookDeliveryResult skipped(String eventId, String endpointId, String reason) {
        return new WebhookDeliveryResult(
                eventId, endpointId, 0, false, true, reason, 0, Duration.ZERO, Instant.now());
    }
}