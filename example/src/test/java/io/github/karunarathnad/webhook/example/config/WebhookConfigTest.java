package io.github.karunarathnad.webhook.example.config;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookConfigTest {

    private final WebhookConfig webhookConfig = new WebhookConfig();

    @Test
    void primaryEndpoint_isSignedAndSubscribedToEveryOrderEvent() {
        WebhookEndpoint endpoint = webhookConfig.primaryEndpoint("whsec_test-secret");

        assertThat(endpoint.id()).isEqualTo("primary-endpoint");
        assertThat(endpoint.targetUrl()).isEqualTo("http://localhost:8080/receive/webhooks");
        assertThat(endpoint.secret()).isEqualTo("whsec_test-secret");
        assertThat(endpoint.subscribedEventTypes())
                .containsExactlyInAnyOrder("order.created", "order.updated", "order.cancelled");
    }

    @Test
    void analyticsEndpoint_isUnsignedAndOnlySubscribedToOrderCreated() {
        WebhookEndpoint endpoint = webhookConfig.analyticsEndpoint();

        assertThat(endpoint.id()).isEqualTo("analytics-endpoint");
        assertThat(endpoint.targetUrl()).isEqualTo("http://localhost:8080/receive/webhooks");
        assertThat(endpoint.secret()).isNull();
        assertThat(endpoint.subscribedEventTypes()).containsExactly("order.created");
        assertThat(endpoint.headers())
                .containsEntry("X-Api-Key", "analytics-api-key-xyz")
                .containsEntry("X-Source", "order-service");
    }
}
