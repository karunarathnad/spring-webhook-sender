package io.github.karunarathnad.webhook.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookEndpoint(
        String id,
        String targetUrl,
        String secret,
        Set<String> subscribedEventTypes
) {
    public WebhookEndpoint {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetUrl, "targetUrl must not be null");
        subscribedEventTypes = subscribedEventTypes != null ? Set.copyOf(subscribedEventTypes) : Set.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String targetUrl;
        private String secret;
        private final Set<String> subscribedEventTypes = new HashSet<>();

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder targetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }

        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        public Builder subscribedEventTypes(Set<String> eventTypes) {
            this.subscribedEventTypes.addAll(eventTypes);
            return this;
        }

        public Builder subscribedEventType(String eventType) {
            this.subscribedEventTypes.add(eventType);
            return this;
        }

        public WebhookEndpoint build() {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(targetUrl, "targetUrl is required");
            return new WebhookEndpoint(id, targetUrl, secret, subscribedEventTypes);
        }
    }
}