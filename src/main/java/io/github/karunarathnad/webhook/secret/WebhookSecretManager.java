package io.github.karunarathnad.webhook.secret;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;

/**
 * Manages the lifecycle of webhook signing secrets.
 *
 * <p>Secrets are used by the sender to compute HMAC-SHA256 signatures and by
 * receivers to verify that requests genuinely originated from this application.
 * Periodically rotating secrets limits the blast radius of a leaked credential.
 *
 * <p>The default implementation ({@link DefaultWebhookSecretManager}) generates
 * secrets from {@link java.security.SecureRandom} and prefixes them with
 * {@code whsec_} for easy identification. Override this bean to integrate with an
 * external key management service:
 *
 * <pre>{@code
 * @Bean
 * public WebhookSecretManager webhookSecretManager(VaultClient vault) {
 *     return new WebhookSecretManager() {
 *
 *         @Override
 *         public String generateSecret() {
 *             return vault.createSecret("webhook");
 *         }
 *
 *         @Override
 *         public WebhookEndpoint rotateSecret(WebhookEndpoint endpoint) {
 *             return WebhookEndpoint.builder()
 *                     .id(endpoint.id())
 *                     .targetUrl(endpoint.targetUrl())
 *                     .subscribedEventTypes(endpoint.subscribedEventTypes())
 *                     .secret(generateSecret())
 *                     .build();
 *         }
 *     };
 * }
 * }</pre>
 */
public interface WebhookSecretManager {

    /**
     * Generates a new cryptographically strong signing secret.
     *
     * <p>The returned value is safe to store and use immediately as the
     * {@code secret} field of a {@link WebhookEndpoint}. Share it with the
     * receiving endpoint owner so they can verify incoming signatures.
     *
     * @return a new signing secret; never {@code null} or empty
     */
    String generateSecret();

    /**
     * Returns a copy of the given endpoint with a newly generated secret, leaving
     * all other fields unchanged.
     *
     * <p>The original endpoint is not modified. Callers are responsible for
     * distributing the new secret to receiving systems before switching to the
     * returned endpoint to avoid a verification gap.
     *
     * @param endpoint the endpoint whose secret should be rotated; must not be {@code null}
     * @return a new endpoint with a fresh secret and the same id, url, and subscriptions
     */
    WebhookEndpoint rotateSecret(WebhookEndpoint endpoint);
}