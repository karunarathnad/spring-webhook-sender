package io.github.karunarathnad.webhook.core;

import java.util.Objects;

public record WebhookEndpoint(
        String id,
        String targetUrl,
        String secret
) {
    public WebhookEndpoint {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetUrl, "targetUrl must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String targetUrl;
        private String secret;

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

        public WebhookEndpoint build() {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(targetUrl, "targetUrl is required");
            return new WebhookEndpoint(id, targetUrl, secret);
        }
    }
}
