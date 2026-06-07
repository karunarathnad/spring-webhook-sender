package io.github.karunarathnad.webhook.example.service;

import io.github.karunarathnad.webhook.core.WebhookClient;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.example.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final WebhookClient webhookClient;
    private final WebhookEndpoint primaryEndpoint;
    private final WebhookEndpoint analyticsEndpoint;

    public OrderService(WebhookClient webhookClient,
                        WebhookEndpoint primaryEndpoint,
                        WebhookEndpoint analyticsEndpoint) {
        this.webhookClient = webhookClient;
        this.primaryEndpoint = primaryEndpoint;
        this.analyticsEndpoint = analyticsEndpoint;
    }

    public Order createOrder(String customerId, String product, BigDecimal amount) {
        Order order = new Order(UUID.randomUUID().toString(), customerId, product, amount, "CREATED");

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of(
                        "orderId", order.id(),
                        "customerId", order.customerId(),
                        "product", order.product(),
                        "amount", order.amount(),
                        "status", order.status()
                ))
                .metadata(Map.of("source", "order-service", "version", "1.0"))
                .build();

        // Blocking send to primary endpoint — good for flows where you need
        // to confirm delivery before proceeding (e.g. financial events).
        WebhookDeliveryResult primary = webhookClient.send(event, primaryEndpoint);
        log.info("Primary endpoint: success={} skipped={}", primary.success(), primary.skipped());

        // Non-blocking send to analytics endpoint — fire-and-forget.
        // The OrderDeliveryListener handles success/failure callbacks.
        webhookClient.sendAsync(event, analyticsEndpoint)
                .thenAccept(r -> log.info("Analytics endpoint: success={} skipped={}", r.success(), r.skipped()));

        return order;
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = new Order(orderId, "customer-1", "Unknown", BigDecimal.ZERO, newStatus);

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.updated")
                .payload(Map.of("orderId", orderId, "newStatus", newStatus))
                .build();

        // The analytics endpoint only subscribes to "order.created", so this
        // delivery will be automatically skipped for that endpoint.
        webhookClient.sendAsync(event, primaryEndpoint);
        webhookClient.sendAsync(event, analyticsEndpoint);

        return order;
    }

    public void cancelOrder(String orderId) {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.cancelled")
                .payload(Map.of("orderId", orderId, "reason", "customer_request"))
                .build();

        webhookClient.sendAsync(event, primaryEndpoint);
        webhookClient.sendAsync(event, analyticsEndpoint);
    }
}
