package io.github.karunarathnad.webhook.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable description of a webhook destination endpoint.
 *
 * <p>An endpoint defines where a webhook event should be delivered, which secret
 * is used to sign the request, and which event types the endpoint cares about.
 * Endpoint instances are typically created once at startup and reused across many
 * delivery calls.
 *
 * <p>Build endpoints through the fluent {@link #builder()}:
 *
 * <pre>{@code
 * WebhookEndpoint endpoint = WebhookEndpoint.builder()
 *         .id("payment-service")
 *         .targetUrl("https://payments.example.com/hooks")
 *         .secret(System.getenv("PAYMENT_WEBHOOK_SECRET"))
 *         .subscribedEventType("order.created")
 *         .subscribedEventType("order.cancelled")
 *         .build();
 * }</pre>
 *
 * <p>The {@code id} field doubles as the circuit-breaker key, so use a stable and
 * unique value per destination. A tripped circuit on one endpoint has no effect
 * on other endpoints in the same application.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookEndpoint(
        String id,
        String targetUrl,
        String secret,
        Set<String> subscribedEventTypes,
        Map<String, String> headers
) {
    private static final Set<String> RESERVED_HEADERS = Set.of("content-type", "accept", "x-webhook-signature");

    public WebhookEndpoint {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetUrl, "targetUrl must not be null");
        subscribedEventTypes = subscribedEventTypes != null ? Set.copyOf(subscribedEventTypes) : Set.of();
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }

    /**
     * Returns a new builder for constructing a {@code WebhookEndpoint}.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for {@link WebhookEndpoint}.
     */
    public static final class Builder {
        private String id;
        private String targetUrl;
        private String secret;
        private final Set<String> subscribedEventTypes = new HashSet<>();
        private final Map<String, String> headers = new HashMap<>();

        private Builder() {}

        /**
         * Sets the unique identifier for this endpoint.
         *
         * <p>The identifier is used as the circuit-breaker and retry registry key,
         * so it must be stable and unique across all endpoints in the application.
         * A name that reflects the consuming service works well, for example
         * {@code "billing-service"} or {@code "crm-webhook"}.
         *
         * @param id the endpoint identifier; must not be {@code null}
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the URL that webhook events will be HTTP POST-ed to.
         *
         * @param targetUrl the destination URL; must not be {@code null}
         * @return this builder
         */
        public Builder targetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }

        /**
         * Sets the HMAC secret used to sign outgoing requests.
         *
         * <p>When a secret is present the library computes an HMAC-SHA256 digest of
         * the JSON body and attaches it as the {@code X-Webhook-Signature} header in
         * the format {@code sha256=<hex>}. When no secret is set the signature header
         * is omitted entirely.
         *
         * <p>Use {@link io.github.karunarathnad.webhook.secret.WebhookSecretManager#generateSecret()}
         * to produce a cryptographically strong secret with a {@code whsec_} prefix.
         *
         * @param secret the signing secret; may be {@code null} to disable signing
         * @return this builder
         */
        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        /**
         * Restricts the endpoint to the given set of event types, replacing any
         * previously added types.
         *
         * <p>When the set is non-empty, events whose type is not in the set are
         * silently skipped without making an HTTP call. An empty set means the
         * endpoint receives all event types, which is the default behaviour.
         *
         * @param eventTypes the event types this endpoint should receive; must not be {@code null}
         * @return this builder
         */
        public Builder subscribedEventTypes(Set<String> eventTypes) {
            this.subscribedEventTypes.addAll(eventTypes);
            return this;
        }

        /**
         * Adds a single event type to the endpoint's subscription list.
         *
         * <p>Calling this method multiple times accumulates event types. Once the
         * subscription list is non-empty, only listed types are delivered.
         *
         * @param eventType the event type to subscribe to; must not be {@code null}
         * @return this builder
         */
        public Builder subscribedEventType(String eventType) {
            this.subscribedEventTypes.add(eventType);
            return this;
        }

        /**
         * Adds a single custom HTTP header to every request sent to this endpoint.
         *
         * <p>Headers are passed through to the consumer as-is. The following headers are
         * managed by the library and cannot be overridden:
         * <ul>
         *   <li>{@code Content-Type}</li>
         *   <li>{@code Accept}</li>
         *   <li>{@code X-Webhook-Signature} (set by the configured {@link io.github.karunarathnad.webhook.signature.SignatureStrategy})</li>
         * </ul>
         *
         * @param key   the header name; must not be {@code null}
         * @param value the header value; must not be {@code null}
         * @return this builder
         * @throws IllegalArgumentException if the header name is reserved by the library
         */
        public Builder header(String key, String value) {
            Objects.requireNonNull(key, "header key must not be null");
            Objects.requireNonNull(value, "header value must not be null");
            if (RESERVED_HEADERS.contains(key.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Header '" + key + "' is managed by the library and cannot be overridden");
            }
            this.headers.put(key, value);
            return this;
        }

        /**
         * Constructs the {@link WebhookEndpoint}.
         *
         * @return a new, immutable endpoint
         * @throws NullPointerException if {@code id} or {@code targetUrl} has not been set
         */
        public WebhookEndpoint build() {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(targetUrl, "targetUrl is required");
            return new WebhookEndpoint(id, targetUrl, secret, subscribedEventTypes, headers);
        }
    }
}