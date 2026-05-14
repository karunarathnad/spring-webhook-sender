package io.github.karunarathnad.webhook.secret;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;

public interface WebhookSecretManager {

    String generateSecret();

    WebhookEndpoint rotateSecret(WebhookEndpoint endpoint);
}