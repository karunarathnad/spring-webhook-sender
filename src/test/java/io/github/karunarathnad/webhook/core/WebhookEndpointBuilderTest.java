package io.github.karunarathnad.webhook.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void headersMapReplacesPreviouslyAddedHeaders() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .header("X-Old", "old-value")
                .headers(Map.of("X-Tenant-ID", "acme-corp", "X-Source-System", "order-service"))
                .build();

        assertThat(endpoint.headers())
                .hasSize(2)
                .containsEntry("X-Tenant-ID", "acme-corp")
                .containsEntry("X-Source-System", "order-service")
                .doesNotContainKey("X-Old");
    }

    @Test
    void headerAfterHeadersMapAccumulates() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .headers(Map.of("X-Tenant-ID", "acme-corp"))
                .header("X-Source-System", "order-service")
                .build();

        assertThat(endpoint.headers())
                .hasSize(2)
                .containsEntry("X-Tenant-ID", "acme-corp")
                .containsEntry("X-Source-System", "order-service");
    }

    @Test
    void headersMapRejectsReservedHeaderNames() {
        WebhookEndpoint.Builder builder = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com");

        assertThatThrownBy(() -> builder.headers(Map.of("Content-Type", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyHeadersMapDefaultsToEmpty() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .headers(Map.of())
                .build();

        assertThat(endpoint.headers()).isEmpty();
    }

    @Test
    void subscribedEventTypesRejectsNullEagerlyWithoutClearingExistingState() {
        WebhookEndpoint.Builder builder = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com")
                .subscribedEventType("order.created");

        assertThatThrownBy(() -> builder.subscribedEventTypes(null))
                .isInstanceOf(NullPointerException.class);

        // The null argument must be rejected before any existing state is mutated.
        assertThat(builder.build().subscribedEventTypes()).containsExactly("order.created");
    }

    @Test
    void canonicalConstructorRejectsReservedHeaderNames() {
        // Reserved-header validation must hold even when WebhookEndpoint is constructed
        // directly (or deserialised from JSON), bypassing the builder entirely.
        assertThatThrownBy(() -> new WebhookEndpoint(
                "test", "https://example.com", null, Set.of(), Map.of("Content-Type", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildRequiresId() {
        WebhookEndpoint.Builder builder = WebhookEndpoint.builder()
                .targetUrl("https://example.com");

        assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildRequiresTargetUrl() {
        WebhookEndpoint.Builder builder = WebhookEndpoint.builder()
                .id("test");

        assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
    }

    @Test
    void headerRejectsNullKey() {
        WebhookEndpoint.Builder builder = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com");

        assertThatThrownBy(() -> builder.header(null, "value"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void headerRejectsNullValue() {
        WebhookEndpoint.Builder builder = WebhookEndpoint.builder()
                .id("test")
                .targetUrl("https://example.com");

        assertThatThrownBy(() -> builder.header("X-Custom", null))
                .isInstanceOf(NullPointerException.class);
    }
}