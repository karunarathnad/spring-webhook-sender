package io.github.karunarathnad.webhook.exception;

public class WebhookRateLimitedException extends WebhookDeliveryException {

    private final long retryAfterMs;

    public WebhookRateLimitedException(long retryAfterMs) {
        super("Rate limited by endpoint (HTTP 429)", 429);
        this.retryAfterMs = retryAfterMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}