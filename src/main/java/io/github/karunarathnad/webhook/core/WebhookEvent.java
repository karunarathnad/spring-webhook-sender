package io.github.karunarathnad.webhook.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable description of a webhook event to be delivered to one or more endpoints.
 *
 * <p>Each event carries a unique identifier, a type string that consumers use for
 * routing and filtering, an arbitrary payload object serialised to JSON, and an
 * optional metadata map for contextual information that does not belong in the payload
 * itself (for example, the originating service name or a correlation identifier).
 *
 * <p>Build events through the fluent {@link #builder()} rather than the canonical
 * constructor. The {@code eventId} and {@code occurredAt} fields default to a random
 * UUID and the current instant respectively, so most callers only need to supply
 * {@code eventType} and {@code payload}:
 *
 * <pre>{@code
 * WebhookEvent event = WebhookEvent.builder()
 *         .eventType("order.created")
 *         .payload(order)
 *         .metadata("source", "order-service")
 *         .build();
 * }</pre>
 *
 * <p>The serialised JSON body sent to endpoints looks like this:
 *
 * <pre>{@code
 * {
 *   "eventId":    "a3f1c2d4-...",
 *   "eventType":  "order.created",
 *   "occurredAt": "2024-09-01T10:30:00Z",
 *   "metadata":   { "source": "order-service" },
 *   "payload":    { "orderId": "ORD-001", "amount": 99.99 }
 * }
 * }</pre>
 */
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

    /**
     * Returns a new builder for constructing a {@code WebhookEvent}.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for {@link WebhookEvent}.
     *
     * <p>The {@code eventId} is pre-populated with a random UUID and {@code occurredAt}
     * with the current instant. Override either field when a stable or externally
     * assigned identifier is required.
     */
    public static final class Builder {
        private String eventId = UUID.randomUUID().toString();
        private String eventType;
        private Object payload;
        private Instant occurredAt = Instant.now();
        private final Map<String, String> metadata = new HashMap<>();

        private Builder() {}

        /**
         * Sets the unique identifier for this event.
         *
         * <p>Defaults to a randomly generated UUID. Override this when the identifier
         * must come from an external system or must remain stable across retries.
         *
         * @param eventId the event identifier; must not be {@code null}
         * @return this builder
         */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * Sets the event type, which receivers use to route and handle the event.
         *
         * <p>Use a dot-separated naming convention such as {@code order.created} or
         * {@code payment.refunded} for consistency across services.
         *
         * @param eventType the event type; must not be {@code null}
         * @return this builder
         */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * Sets the payload to be serialised as the {@code payload} field in the JSON body.
         *
         * <p>Any Jackson-serialisable object is accepted. The object is converted to JSON
         * at dispatch time using the library's configured {@code ObjectMapper}.
         *
         * @param payload the payload; must not be {@code null}
         * @return this builder
         */
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Sets the timestamp indicating when the event occurred in the originating system.
         *
         * <p>Defaults to the instant the builder was created. Set this explicitly when
         * the event represents something that happened at a different point in time.
         *
         * @param occurredAt the occurrence timestamp; must not be {@code null}
         * @return this builder
         */
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        /**
         * Adds a single metadata entry.
         *
         * <p>Metadata is intended for cross-cutting contextual information such as
         * the originating service, a trace identifier, or an environment name. Keep
         * domain data in the payload instead.
         *
         * @param key   the metadata key; must not be {@code null}
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Adds all entries from the given map to the metadata.
         *
         * @param metadata the entries to add; must not be {@code null}
         * @return this builder
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Constructs the {@link WebhookEvent}.
         *
         * @return a new, immutable event
         * @throws NullPointerException if {@code eventType} or {@code payload} has not been set
         */
        public WebhookEvent build() {
            Objects.requireNonNull(eventType, "eventType is required");
            Objects.requireNonNull(payload, "payload is required");
            return new WebhookEvent(eventId, eventType, payload, occurredAt, metadata);
        }
    }
}