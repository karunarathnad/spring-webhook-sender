package io.github.karunarathnad.webhook.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryResultTest {

    @Test
    void successFactorySetsExpectedFields() {
        WebhookDeliveryResult result = WebhookDeliveryResult.success(
                "event-1", "endpoint-1", 200, 2, Duration.ofMillis(150));

        assertThat(result.eventId()).isEqualTo("event-1");
        assertThat(result.endpointId()).isEqualTo("endpoint-1");
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.success()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.totalAttempts()).isEqualTo(2);
        assertThat(result.totalDuration()).isEqualTo(Duration.ofMillis(150));
        assertThat(result.deliveredAt()).isNotNull();
    }

    @Test
    void failureFactorySetsExpectedFields() {
        WebhookDeliveryResult result = WebhookDeliveryResult.failure(
                "event-2", "endpoint-2", 500, "Server error 500", 3, Duration.ofSeconds(1));

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(500);
        assertThat(result.errorMessage()).isEqualTo("Server error 500");
        assertThat(result.totalAttempts()).isEqualTo(3);
        assertThat(result.totalDuration()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void skippedFactorySetsZeroAttemptsAndZeroDuration() {
        WebhookDeliveryResult result = WebhookDeliveryResult.skipped(
                "event-3", "endpoint-3", "Event type 'x' not subscribed at endpoint 'endpoint-3'");

        assertThat(result.skipped()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isZero();
        assertThat(result.totalAttempts()).isZero();
        assertThat(result.totalDuration()).isEqualTo(Duration.ZERO);
        assertThat(result.errorMessage()).contains("not subscribed");
    }
}
