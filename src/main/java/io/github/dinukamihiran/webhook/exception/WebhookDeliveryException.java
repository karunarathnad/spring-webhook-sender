package io.github.karunarathnad.webhook.exception;

public class WebhookDeliveryException extends RuntimeException {

    private final int httpStatus;

    public WebhookDeliveryException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public WebhookDeliveryException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** 4xx errors — do not retry. */
    public static class NonRetryable extends WebhookDeliveryException {
        public NonRetryable(String message, int httpStatus) {
            super(message, httpStatus);
        }
    }
}
