package io.github.karunarathnad.webhook.secret;

import io.github.karunarathnad.webhook.core.WebhookEndpoint;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * The default {@link WebhookSecretManager} registered by the library's auto-configuration.
 *
 * <p>Secrets are generated from {@link SecureRandom} using 32 bytes of random data,
 * encoded as lowercase hexadecimal, and prefixed with {@code whsec_} for easy
 * identification in configuration files and logs. The resulting secret is 70 characters
 * long and provides 256 bits of entropy.
 *
 * <p>Override the {@code webhookSecretManager} bean to integrate with an external
 * key management service such as HashiCorp Vault or AWS Secrets Manager.
 */
public class DefaultWebhookSecretManager implements WebhookSecretManager {

    private static final String PREFIX = "whsec_";
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a new signing secret backed by {@link SecureRandom}.
     *
     * @return a cryptographically strong secret prefixed with {@code whsec_};
     *         never {@code null}
     */
    @Override
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * Returns a copy of the endpoint with a newly generated secret.
     *
     * <p>The {@code id}, {@code targetUrl}, {@code subscribedEventTypes}, and {@code headers}
     * fields are carried over unchanged. The original endpoint instance is not modified.
     *
     * @param endpoint the endpoint to rotate; must not be {@code null}
     * @return a new endpoint with a fresh secret
     */
    @Override
    public WebhookEndpoint rotateSecret(WebhookEndpoint endpoint) {
        var builder = WebhookEndpoint.builder()
                .id(endpoint.id())
                .targetUrl(endpoint.targetUrl())
                .subscribedEventTypes(endpoint.subscribedEventTypes())
                .secret(generateSecret());
        
        // Copy custom headers from the original endpoint
        for (var entry : endpoint.headers().entrySet()) {
            builder = builder.header(entry.getKey(), entry.getValue());
        }
        
        return builder.build();
    }
}