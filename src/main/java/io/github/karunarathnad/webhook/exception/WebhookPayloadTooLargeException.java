package io.github.karunarathnad.webhook.exception;

public class WebhookPayloadTooLargeException extends WebhookDeliveryException.NonRetryable {

    public WebhookPayloadTooLargeException(int actualBytes, int maxBytes) {
        super(String.format("Payload size %d bytes exceeds maximum of %d bytes", actualBytes, maxBytes), -1);
    }
}