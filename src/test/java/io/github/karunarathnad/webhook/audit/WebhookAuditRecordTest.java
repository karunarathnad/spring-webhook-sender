package io.github.karunarathnad.webhook.audit;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookAuditRecordTest {

    @Test
    void ofMapsAllFieldsFromEventAndEndpoint() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("billing-service")
                .targetUrl("https://billing.example.com/hooks")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of("orderId", "ORD-1"))
                .build();

        WebhookAuditRecord record = WebhookAuditRecord.of(
                event, endpoint, 200, true, null, 1, 42L);

        assertThat(record.eventId()).isEqualTo(event.eventId());
        assertThat(record.eventType()).isEqualTo("order.created");
        assertThat(record.endpointId()).isEqualTo("billing-service");
        assertThat(record.targetUrl()).isEqualTo("https://billing.example.com/hooks");
        assertThat(record.httpStatusCode()).isEqualTo(200);
        assertThat(record.success()).isTrue();
        assertThat(record.errorMessage()).isNull();
        assertThat(record.attemptNumber()).isEqualTo(1);
        assertThat(record.durationMs()).isEqualTo(42L);
        assertThat(record.timestamp()).isNotNull();
    }

    @Test
    void ofMapsFailureFields() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("billing-service")
                .targetUrl("https://billing.example.com/hooks")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of())
                .build();

        WebhookAuditRecord record = WebhookAuditRecord.of(
                event, endpoint, 503, false, "Server error 503", 2, 99L);

        assertThat(record.success()).isFalse();
        assertThat(record.httpStatusCode()).isEqualTo(503);
        assertThat(record.errorMessage()).isEqualTo("Server error 503");
        assertThat(record.attemptNumber()).isEqualTo(2);
    }
}
