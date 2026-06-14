package io.github.karunarathnad.webhook.secret;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSecretManagerTest {

    private final WebhookSecretManager secretManager = new DefaultWebhookSecretManager();

    @Test
    void generateSecretReturnsValidFormat() {
        String secret = secretManager.generateSecret();
        assertThat(secret)
                .startsWith("whsec_")
                .hasSize(70)
                .matches("whsec_[a-f0-9]{64}");
    }

    @Test
    void rotateSecretPreservesEndpointConfiguration() {
        WebhookEndpoint original = WebhookEndpoint.builder()
                .id("test-endpoint")
                .targetUrl("https://example.com/webhooks")
                .secret("whsec_old")
                .subscribedEventType("order.created")
                .subscribedEventType("order.updated")
                .header("X-Api-Key", "key-123")
                .header("X-Tenant-Id", "tenant-abc")
                .build();

        WebhookEndpoint rotated = secretManager.rotateSecret(original);

        // Verify endpoint id, url, subscriptions are preserved
        assertThat(rotated.id()).isEqualTo(original.id());
        assertThat(rotated.targetUrl()).isEqualTo(original.targetUrl());
        assertThat(rotated.subscribedEventTypes()).isEqualTo(original.subscribedEventTypes());

         assertThat(rotated.headers()).isEqualTo(original.headers());
        assertThat(rotated.headers().get("X-Api-Key")).isEqualTo("key-123");
        assertThat(rotated.headers().get("X-Tenant-Id")).isEqualTo("tenant-abc");

        // Verify secret changed
        assertThat(rotated.secret())
                .isNotEqualTo(original.secret())
                .startsWith("whsec_")
                .hasSize(70);
    }

    @Test
    void rotateSecretWithoutHeadersWorks() {
        WebhookEndpoint original = WebhookEndpoint.builder()
                .id("simple-endpoint")
                .targetUrl("https://example.com/hook")
                .secret("whsec_original")
                .build();

        WebhookEndpoint rotated = secretManager.rotateSecret(original);

        assertThat(rotated.id()).isEqualTo(original.id());
        assertThat(rotated.headers()).isEmpty();
        assertThat(rotated.secret()).isNotEqualTo(original.secret());
    }

    @Test
    void generatedSecretsAreUnique() {
        String secret1 = secretManager.generateSecret();
        String secret2 = secretManager.generateSecret();
        String secret3 = secretManager.generateSecret();

        assertThat(secret1).isNotEqualTo(secret2).isNotEqualTo(secret3);
    }
}

