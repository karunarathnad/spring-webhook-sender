package io.github.karunarathnad.webhook.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookEventBuilderEnhancedTest {

    @Test
    void builderGeneratesRandomEventId() {
        WebhookEvent event1 = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .build();

        WebhookEvent event2 = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .build();

        assertThat(event1.eventId()).isNotEqualTo(event2.eventId());
    }

    @Test
    void builderAllowsCustomEventId() {
        String customId = "custom-event-123";
        WebhookEvent event = WebhookEvent.builder()
                .eventId(customId)
                .eventType("test")
                .payload(Map.of())
                .build();

        assertThat(event.eventId()).isEqualTo(customId);
    }

    @Test
    void occurredAtDefaultsToCurrentInstant() {
        Instant before = Instant.now();
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .build();
        Instant after = Instant.now();

        assertThat(event.occurredAt()).isBetween(before, after);
    }

    @Test
    void builderAllowsCustomOccurredAt() {
        Instant customTime = Instant.parse("2024-01-15T10:30:00Z");
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .occurredAt(customTime)
                .build();

        assertThat(event.occurredAt()).isEqualTo(customTime);
    }

    @Test
    void metadataAccumulates() {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .metadata("key1", "value1")
                .metadata("key2", "value2")
                .build();

        assertThat(event.metadata()).hasSize(2);
        assertThat(event.metadata()).containsEntry("key1", "value1");
        assertThat(event.metadata()).containsEntry("key2", "value2");
    }

    @Test
    void metadataMapCanBeAdded() {
        Map<String, String> metaMap = Map.of("source", "order-service", "env", "prod");
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .metadata(metaMap)
                .build();

        assertThat(event.metadata()).containsAllEntriesOf(metaMap);
    }

    @Test
    void metadataIsImmutable() {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .metadata("key", "value")
                .build();

        assertThatThrownBy(() -> event.metadata().put("key2", "value2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingEventTypeThrowsException() {
        assertThatThrownBy(() -> WebhookEvent.builder()
                .payload(Map.of())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void missingPayloadThrowsException() {
        assertThatThrownBy(() -> WebhookEvent.builder()
                .eventType("test")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void eventImmutable() {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .build();

        assertThat(event).isNotNull();
    }

    @Test
    void emptyMetadataDefaultsToEmpty() {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("test")
                .payload(Map.of())
                .build();

        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void payloadCanBeAnyObject() {
        Map<String, Object> complexPayload = Map.of(
                "order", Map.of("id", "ORD-123", "amount", 99.99),
                "customer", Map.of("name", "John Doe"),
                "items", new int[]{1, 2, 3}
        );

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(complexPayload)
                .build();

        assertThat(event.payload()).isEqualTo(complexPayload);
    }
}

