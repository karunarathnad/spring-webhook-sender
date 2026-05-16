package io.github.karunarathnad.webhook.exception;

/**
 * Signals that a webhook delivery attempt failed.
 *
 * <p>The library throws this exception internally during HTTP dispatch. It carries
 * the HTTP status code from the endpoint's response, or {@code -1} when the failure
 * was a network error and no response was received.
 *
 * <p>Use the {@link NonRetryable} subclass to indicate failures that should not trigger
 * a retry. The retry and circuit-breaker machinery treats any exception that is not a
 * {@code NonRetryable} as a transient error and will attempt the delivery again
 * according to the configured retry policy.
 */
public class WebhookDeliveryException extends RuntimeException {

    private final int httpStatus;

    /**
     * Creates an exception with a descriptive message and the HTTP status code
     * from the endpoint's response.
     *
     * @param message    a description of the failure
     * @param httpStatus the HTTP status code returned by the endpoint
     */
    public WebhookDeliveryException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * Creates an exception wrapping a lower-level cause, typically a network or
     * I/O error. The HTTP status is set to {@code -1} because no response was received.
     *
     * @param message a description of the failure
     * @param cause   the underlying exception
     */
    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    /**
     * Creates an exception with a descriptive message, an HTTP status code, and an
     * underlying cause.
     *
     * @param message    a description of the failure
     * @param httpStatus the HTTP status code returned by the endpoint
     * @param cause      the underlying exception
     */
    public WebhookDeliveryException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the HTTP status code associated with this failure.
     *
     * @return the HTTP status code, or {@code -1} when no HTTP response was received
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * A delivery failure that should not be retried.
     *
     * <p>Thrown for 4xx client errors (excluding 429) where retrying would produce
     * the same outcome. The circuit breaker does not count these exceptions toward
     * its failure rate, since they reflect a problem with the request rather than
     * the health of the endpoint.
     */
    public static class NonRetryable extends WebhookDeliveryException {

        /**
         * Creates a non-retryable exception with a descriptive message and HTTP status.
         *
         * @param message    a description of the failure
         * @param httpStatus the HTTP status code returned by the endpoint
         */
        public NonRetryable(String message, int httpStatus) {
            super(message, httpStatus);
        }
    }
}