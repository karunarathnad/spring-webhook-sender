package io.github.karunarathnad.webhook.core;

import io.github.karunarathnad.webhook.WebhookTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EventTypeSubscriptionFilteringTest {

    @Autowired
    private WebhookClient webhookClient;

    @Test
    void skipsEventWhenTypeNotSubscribed() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("filtered-endpoint")
                .targetUrl("https://example.com/webhooks")
                .secret("secret")
                .subscribedEventType("order.created")
                .subscribedEventType("order.shipped")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("payment.received")
                .payload(Map.of("amount", 100.0))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.skipped()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(0);
        assertThat(result.totalAttempts()).isEqualTo(0);
        assertThat(result.errorMessage()).contains("not subscribed", "payment.received");
    }

    @Test
    void allowsAllEventsWhenSubscriptionIsEmpty() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("all-events-endpoint")
                .targetUrl("https://example.com/webhooks")
                .secret("secret")
                .build();

        assertThat(endpoint.subscribedEventTypes()).isEmpty();
    }

    @Test
    void skipsMultipleEventsWithDifferentTypes() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("order-only")
                .targetUrl("https://example.com/webhooks")
                .subscribedEventType("order.created")
                .build();

        WebhookEvent paymentEvent = WebhookEvent.builder()
                .eventType("payment.processed")
                .payload(Map.of())
                .build();

        WebhookEvent shippingEvent = WebhookEvent.builder()
                .eventType("shipping.started")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result1 = webhookClient.send(paymentEvent, endpoint);
        WebhookDeliveryResult result2 = webhookClient.send(shippingEvent, endpoint);

        assertThat(result1.skipped()).isTrue();
        assertThat(result2.skipped()).isTrue();
    }

    @Test
    void skippedEventReturnsZeroDuration() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test-skip")
                .targetUrl("https://example.com/webhooks")
                .subscribedEventType("order.created")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("payment.received")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.totalDuration().toMillis()).isEqualTo(0);
    }

    @Test
    void subscribedEventTypeIsDelivered() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("subscribed")
                .targetUrl("https://example.com/webhooks")
                .subscribedEventType("order.created")
                .subscribedEventType("order.cancelled")
                .build();

        assertThat(endpoint.subscribedEventTypes())
                .containsExactlyInAnyOrder("order.created", "order.cancelled");
    }
}

