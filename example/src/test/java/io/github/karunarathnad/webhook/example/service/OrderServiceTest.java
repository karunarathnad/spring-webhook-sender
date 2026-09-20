package io.github.karunarathnad.webhook.example.service;

import io.github.karunarathnad.webhook.core.WebhookClient;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.example.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private WebhookClient webhookClient;

    private WebhookEndpoint primaryEndpoint;
    private WebhookEndpoint analyticsEndpoint;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        primaryEndpoint = WebhookEndpoint.builder()
                .id("primary-endpoint")
                .targetUrl("http://localhost:8080/receive/webhooks")
                .subscribedEventTypes(Set.of("order.created", "order.updated", "order.cancelled"))
                .build();
        analyticsEndpoint = WebhookEndpoint.builder()
                .id("analytics-endpoint")
                .targetUrl("http://localhost:8080/receive/webhooks")
                .subscribedEventTypes(Set.of("order.created"))
                .build();
        orderService = new OrderService(webhookClient, primaryEndpoint, analyticsEndpoint);
    }

    @Test
    void createOrder_returnsOrderWithGeneratedIdAndCreatedStatus() {
        when(webhookClient.send(any(WebhookEvent.class), eq(primaryEndpoint))).thenReturn(successResult());
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(analyticsEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));

        Order order = orderService.createOrder("cust-1", "widget", BigDecimal.TEN);

        assertThat(order.id()).isNotBlank();
        assertThat(order.customerId()).isEqualTo("cust-1");
        assertThat(order.product()).isEqualTo("widget");
        assertThat(order.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(order.status()).isEqualTo("CREATED");
    }

    @Test
    void createOrder_sendsBlockingEventToPrimaryEndpoint_withOrderCreatedType() {
        when(webhookClient.send(any(WebhookEvent.class), eq(primaryEndpoint))).thenReturn(successResult());
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(analyticsEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));

        Order order = orderService.createOrder("cust-1", "widget", BigDecimal.TEN);

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookClient).send(eventCaptor.capture(), eq(primaryEndpoint));
        WebhookEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("order.created");
        assertThat(event.payload()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.payload();
        assertThat(payload).containsEntry("orderId", order.id()).containsEntry("customerId", "cust-1");
    }

    @Test
    void createOrder_sendsNonBlockingEventToAnalyticsEndpoint() {
        when(webhookClient.send(any(WebhookEvent.class), eq(primaryEndpoint))).thenReturn(successResult());
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(analyticsEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));

        orderService.createOrder("cust-1", "widget", BigDecimal.TEN);

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookClient).sendAsync(eventCaptor.capture(), eq(analyticsEndpoint));
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("order.created");
    }

    @Test
    void updateOrderStatus_sendsOrderUpdatedEventToBothEndpointsAsynchronously() {
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(primaryEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(analyticsEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));

        Order order = orderService.updateOrderStatus("order-1", "SHIPPED");

        assertThat(order.id()).isEqualTo("order-1");
        assertThat(order.status()).isEqualTo("SHIPPED");

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookClient).sendAsync(eventCaptor.capture(), eq(primaryEndpoint));
        verify(webhookClient).sendAsync(eventCaptor.capture(), eq(analyticsEndpoint));
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.eventType()).isEqualTo("order.updated"));
    }

    @Test
    void cancelOrder_sendsOrderCancelledEventToBothEndpointsAsynchronously() {
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(primaryEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));
        when(webhookClient.sendAsync(any(WebhookEvent.class), eq(analyticsEndpoint))).thenReturn(CompletableFuture.completedFuture(successResult()));

        orderService.cancelOrder("order-1");

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookClient).sendAsync(eventCaptor.capture(), eq(primaryEndpoint));
        verify(webhookClient).sendAsync(eventCaptor.capture(), eq(analyticsEndpoint));
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.eventType()).isEqualTo("order.cancelled"));
    }

    private static WebhookDeliveryResult successResult() {
        return WebhookDeliveryResult.success("event-1", "endpoint-1", 200, 1, Duration.ofMillis(10));
    }
}
