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

/**
 * Simulates order-management operations, firing a webhook to both the
 * primary and analytics endpoints on every state change. See
 * {@link io.github.karunarathnad.webhook.example.config.WebhookConfig}
 * for how those endpoints are configured.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final WebhookClient webhookClient;
    private final WebhookEndpoint primaryEndpoint;
    private final WebhookEndpoint analyticsEndpoint;

    /**
     * Creates the service with the client and endpoints used to fire order webhooks.
     *
     * @param webhookClient     the client used to send webhook events
     * @param primaryEndpoint   the primary endpoint, sent to on every order state change
     * @param analyticsEndpoint the analytics endpoint, subscribed only to {@code order.created}
     */
    public OrderService(WebhookClient webhookClient,
                        WebhookEndpoint primaryEndpoint,
                        WebhookEndpoint analyticsEndpoint) {
        this.webhookClient = webhookClient;
        this.primaryEndpoint = primaryEndpoint;
        this.analyticsEndpoint = analyticsEndpoint;
    }

    /**
     * Creates an order and fires an {@code order.created} webhook: a blocking send
     * to the primary endpoint followed by a non-blocking send to the analytics endpoint.
     *
     * @param customerId the id of the customer placing the order
     * @param product    the product being ordered
     * @param amount     the order amount
     * @return the created order
     */
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

    /**
     * Updates an order's status and fires an {@code order.updated} webhook to both
     * endpoints; the analytics endpoint skips it since it only subscribes to {@code order.created}.
     *
     * @param orderId   the id of the order to update
     * @param newStatus the new status to apply
     * @return the updated order
     */
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

    /**
     * Cancels an order and fires an {@code order.cancelled} webhook to both
     * endpoints; the analytics endpoint skips it since it only subscribes to {@code order.created}.
     *
     * @param orderId the id of the order to cancel
     */
    public void cancelOrder(String orderId) {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.cancelled")
                .payload(Map.of("orderId", orderId, "reason", "customer_request"))
                .build();

        webhookClient.sendAsync(event, primaryEndpoint);
        webhookClient.sendAsync(event, analyticsEndpoint);
    }
}
