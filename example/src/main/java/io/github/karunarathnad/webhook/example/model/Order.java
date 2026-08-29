package io.github.karunarathnad.webhook.example.model;

import java.math.BigDecimal;

/**
 * An order in the example domain model, returned by {@code OrderController}
 * and used to build the payload of the {@code order.*} webhook events.
 *
 * @param id         the order identifier
 * @param customerId the customer who placed the order
 * @param product    the product name
 * @param amount     the order amount
 * @param status     the current order status (e.g. {@code CREATED}, {@code SHIPPED})
 */
public record Order(
        String id,
        String customerId,
        String product,
        BigDecimal amount,
        String status
) {}
