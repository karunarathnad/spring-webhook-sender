package io.github.karunarathnad.webhook.example.model;

import java.math.BigDecimal;

public record Order(
        String id,
        String customerId,
        String product,
        BigDecimal amount,
        String status
) {}
