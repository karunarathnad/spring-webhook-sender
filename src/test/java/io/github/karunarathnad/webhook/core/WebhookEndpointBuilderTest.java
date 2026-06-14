package io.github.karunarathnad.webhook.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEndpointBuilderTest {

    @Test
    void subscribedEventTypesReplacePreviouslyAddedTypes() {
         WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .subscribedEventType("order.created")
                .subscribedEventType("order.updated")
                .subscribedEventTypes(Set.of("payment.received", "payment.failed"))
                .build();

        assertThat(endpoint.subscribedEventTypes())
                .hasSize(2)
                .containsExactlyInAnyOrder("payment.received", "payment.failed")
                .doesNotContain("order.created", "order.updated");
    }

    @Test
    void subscribedEventTypeAccumulates() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .subscribedEventType("order.created")
                .subscribedEventType("order.updated")
                .build();

        assertThat(endpoint.subscribedEventTypes())
                .hasSize(2)
                .containsExactlyInAnyOrder("order.created", "order.updated");
    }

    @Test
    void emptySubscribedEventTypesDefaultsToEmpty() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .build();

        assertThat(endpoint.subscribedEventTypes()).isEmpty();
    }
}