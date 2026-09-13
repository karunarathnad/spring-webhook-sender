package io.github.karunarathnad.webhook.example.controller;

import io.github.karunarathnad.webhook.example.model.CreateOrderRequest;
import io.github.karunarathnad.webhook.example.model.Order;
import io.github.karunarathnad.webhook.example.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for order management, demonstrating how a typical service layer
 * triggers webhook deliveries from state-changing operations.
 *
 * <p>Each handler delegates to {@link OrderService}, which fires the corresponding
 * {@code order.*} event to the primary and analytics endpoints configured in
 * {@link io.github.karunarathnad.webhook.example.config.WebhookConfig}.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Create an order — fires an order.created webhook to both endpoints. */
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.customerId(), request.product(), request.amount());
        return ResponseEntity.ok(order);
    }

    /** Update order status — fires an order.updated webhook (analytics endpoint skips it). */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Order> updateStatus(
            @PathVariable String orderId,
            @RequestParam String status) {
        if (status.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Order order = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(order);
    }

    /** Cancel an order — fires an order.cancelled webhook (analytics endpoint skips it). */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }
}
