package io.github.karunarathnad.webhook.example.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String product,
        @Positive BigDecimal amount
) {}
