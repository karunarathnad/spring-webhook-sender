package io.github.karunarathnad.webhook.secret;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;

import java.security.SecureRandom;
import java.util.HexFormat;

public class DefaultWebhookSecretManager implements WebhookSecretManager {

    private static final String PREFIX = "whsec_";
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    @Override
    public WebhookEndpoint rotateSecret(WebhookEndpoint endpoint) {
        return WebhookEndpoint.builder()
                .id(endpoint.id())
                .targetUrl(endpoint.targetUrl())
                .subscribedEventTypes(endpoint.subscribedEventTypes())
                .secret(generateSecret())
                .build();
    }
}