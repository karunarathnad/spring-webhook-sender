package io.github.karunarathnad.webhook.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHandlingTest {

    @Test
    void webhookDeliveryExceptionHasHttpStatus() {
        WebhookDeliveryException ex = new WebhookDeliveryException("Test error", 500);
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex.getMessage()).isEqualTo("Test error");
    }

    @Test
    void webhookDeliveryExceptionWithCause() {
        RuntimeException cause = new RuntimeException("Network error");
        WebhookDeliveryException ex = new WebhookDeliveryException("HTTP request failed", cause);
        assertThat(ex.getHttpStatus()).isEqualTo(-1);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void nonRetryableExceptionExtendsDeliveryException() {
        WebhookDeliveryException.NonRetryable ex = new WebhookDeliveryException.NonRetryable("Client error", 401);
        assertThat(ex).isInstanceOf(WebhookDeliveryException.class);
        assertThat(ex.getHttpStatus()).isEqualTo(401);
    }

    @Test
    void payloadTooLargeExceptionIsNonRetryable() {
        WebhookPayloadTooLargeException ex = new WebhookPayloadTooLargeException(5000, 1000);
        assertThat(ex).isInstanceOf(WebhookDeliveryException.NonRetryable.class);
        assertThat(ex.getMessage()).contains("5000").contains("1000");
        assertThat(ex.getHttpStatus()).isEqualTo(-1);
    }

    @Test
    void webhookRateLimitedExceptionStoresRetryAfter() {
        WebhookRateLimitedException ex = new WebhookRateLimitedException(5000);
        assertThat(ex.getRetryAfterMs()).isEqualTo(5000);
    }

    @Test
    void webhookDeliveryExceptionWithStatusAndCause() {
        RuntimeException cause = new RuntimeException("Connection reset");
        WebhookDeliveryException ex = new WebhookDeliveryException("Server error 503", 503, cause);

        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getMessage()).isEqualTo("Server error 503");
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}

