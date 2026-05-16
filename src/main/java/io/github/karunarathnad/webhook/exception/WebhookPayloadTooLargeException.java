package io.github.karunarathnad.webhook.exception;

/**
 * Thrown when a serialised webhook payload exceeds the configured size limit.
 *
 * <p>The check happens after the payload object is serialised to JSON but before the
 * HTTP request is made. Because retrying the same payload would produce the same
 * result, this exception extends {@link WebhookDeliveryException.NonRetryable} and
 * will not trigger a retry attempt.
 *
 * <p>The size limit is controlled by the {@code webhook.max-payload-size-bytes}
 * property, which defaults to 262144 bytes (256 KB). Increase the limit or reduce the
 * payload if this exception occurs in production.
 */
public class WebhookPayloadTooLargeException extends WebhookDeliveryException.NonRetryable {

    /**
     * Creates an exception describing the payload size violation.
     *
     * @param actualBytes the serialised size of the payload in bytes
     * @param maxBytes    the maximum permitted size in bytes
     */
    public WebhookPayloadTooLargeException(int actualBytes, int maxBytes) {
        super(String.format("Payload size %d bytes exceeds maximum of %d bytes", actualBytes, maxBytes), -1);
    }
}