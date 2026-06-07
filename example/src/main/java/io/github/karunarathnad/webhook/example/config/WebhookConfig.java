package io.github.karunarathnad.webhook.example.config;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Configuration
public class WebhookConfig {

    /**
     * Primary endpoint — receives all order events, signed with HMAC-SHA256.
     * The receiver validates the X-Webhook-Signature header on the other side.
     */
    @Bean
    public WebhookEndpoint primaryEndpoint() {
        return WebhookEndpoint.builder()
                .id("primary-endpoint")
                .targetUrl("http://localhost:8080/receive/webhooks")
                .secret("whsec_example-secret-key-replace-in-production")
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
                .headers(Map.of(
                        "X-Api-Key", "analytics-api-key-xyz",
                        "X-Source", "order-service"
                ))
                .build();
    }
}
