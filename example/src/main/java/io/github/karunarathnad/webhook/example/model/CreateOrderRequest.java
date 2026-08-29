package io.github.karunarathnad.webhook.example.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request body for creating an order via {@code POST /orders}.
 *
 * @param customerId the customer placing the order; must not be blank
 * @param product    the product name; must not be blank
 * @param amount     the order amount; must be positive
 */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String product,
        @Positive BigDecimal amount
) {}
