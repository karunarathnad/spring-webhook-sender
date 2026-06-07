package io.github.karunarathnad.webhook.example.controller;

import io.github.karunarathnad.webhook.example.model.CreateOrderRequest;
import io.github.karunarathnad.webhook.example.model.Order;
import io.github.karunarathnad.webhook.example.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Create an order — fires an order.created webhook to both endpoints. */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.customerId(), request.product(), request.amount());
        return ResponseEntity.ok(order);
    }

    /** Update order status — fires an order.updated webhook (analytics endpoint skips it). */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Order> updateStatus(
            @PathVariable String orderId,
            @RequestParam String status) {
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
