package io.github.karunarathnad.webhook.example.config;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class WebhookConfig {

    /**
     * Primary endpoint — receives all order events, signed with HMAC-SHA256.
     * The receiver validates the X-Webhook-Signature header on the other side using the
     * same {@code example.webhook-secret} property (see WebhookReceiverController).
     */
    @Bean
    public WebhookEndpoint primaryEndpoint(@Value("${example.webhook-secret}") String webhookSecret) {
        return WebhookEndpoint.builder()
                .id("primary-endpoint")
                .targetUrl("http://localhost:8080/receive/webhooks")
                .secret(webhookSecret)
                .subscribedEventTypes(Set.of("order.created", "order.updated", "order.cancelled"))
                .build();
    }

    /**
     * Analytics endpoint — only subscribes to order.created events and passes
     * a custom API key header required by the downstream analytics service.
     */
    @Bean
    public WebhookEndpoint analyticsEndpoint() {
        return WebhookEndpoint.builder()
                .id("analytics-endpoint")
                .targetUrl("http://localhost:8080/receive/webhooks")
                .subscribedEventTypes(Set.of("order.created"))
                .header("X-Api-Key", "analytics-api-key-xyz")
                .header("X-Source", "order-service")
                .build();
    }
}
