package io.github.karunarathnad.webhook.audit;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;

import java.time.Instant;

/**
 * An immutable snapshot of a single webhook delivery attempt, used as input to
 * {@link AuditLogger}.
 *
 * <p>One record is produced after each HTTP request, regardless of whether the request
 * succeeded or will be retried. When an event requires three attempts to deliver, three
 * separate records are created with increasing {@code attemptNumber} values.
 *
 * <p>Records are created through the {@link #of} factory method rather than the
 * canonical constructor.
 */
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

    /**
     * Creates an audit record from the context of a completed delivery attempt.
     *
     * @param event        the event that was being delivered
     * @param endpoint     the endpoint the event was sent to
     * @param httpStatus   the HTTP status code returned by the endpoint, or {@code -1}
     *                     for network-level errors where no response was received
     * @param success      {@code true} if the endpoint responded with a 2xx status code
     * @param errorMessage a description of the error, or {@code null} on success
     * @param attempt      the attempt number, starting at 1 for the first try
     * @param durationMs   the time taken for this specific attempt in milliseconds
     * @return a new audit record capturing all of the above
     */
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