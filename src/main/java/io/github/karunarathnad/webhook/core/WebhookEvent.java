package io.github.karunarathnad.webhook.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookEvent(
        String eventId,
        String eventType,
        Object payload,
        Instant occurredAt,
        Map<String, String> metadata
) {
    public WebhookEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId = UUID.randomUUID().toString();
        private String eventType;
        private Object payload;
        private Instant occurredAt = Instant.now();
        private final Map<String, String> metadata = new HashMap<>();

        private Builder() {}

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public WebhookEvent build() {
            Objects.requireNonNull(eventType, "eventType is required");
            Objects.requireNonNull(payload, "payload is required");
            return new WebhookEvent(eventId, eventType, payload, occurredAt, metadata);
        }
    }
}