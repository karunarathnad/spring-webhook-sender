package io.github.karunarathnad.webhook.exception;

/**
 * Thrown when a webhook endpoint responds with HTTP 429 Too Many Requests.
 *
 * <p>Unlike other 4xx responses, a 429 indicates a temporary capacity constraint at the
 * receiving end rather than a problem with the request itself. The library therefore
 * treats it as retryable. When the response includes a {@code Retry-After} header, the
 * value is parsed and stored so that the retry interval function can honour it.
 *
 * <p>The retry will wait at least as long as the endpoint requested, capped at the
 * value of {@code webhook.retry.max-interval} to prevent unbounded waits.
 */
public class WebhookRateLimitedException extends WebhookDeliveryException {

    private final long retryAfterMs;

    /**
     * Creates a rate-limited exception with the suggested retry delay.
     *
     * @param retryAfterMs the delay in milliseconds parsed from the endpoint's
     *                     {@code Retry-After} header, or {@code 0} if the header
     *                     was absent or could not be parsed
     */
    public WebhookRateLimitedException(long retryAfterMs) {
        super("Rate limited by endpoint (HTTP 429)", 429);
        this.retryAfterMs = retryAfterMs;
    }

    /**
     * Returns the suggested delay before the next retry attempt.
     *
     * @return the delay in milliseconds, or {@code 0} if the endpoint did not
     *         provide a {@code Retry-After} header
     */
    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}