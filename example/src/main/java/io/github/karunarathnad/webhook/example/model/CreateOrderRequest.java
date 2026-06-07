package io.github.karunarathnad.webhook.example.model;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String customerId,
        String product,
        BigDecimal amount
) {}
