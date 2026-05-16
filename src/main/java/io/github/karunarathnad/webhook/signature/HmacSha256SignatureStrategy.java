package io.github.karunarathnad.webhook.signature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Signs webhook requests using HMAC-SHA256, following the same convention as
 * GitHub and Stripe webhooks.
 *
 * <p>The computed signature is attached to each request as the
 * {@code X-Webhook-Signature} header in the format {@code sha256=<64-hex-chars>}.
 * Receiving systems can verify the signature by computing the same HMAC over the
 * raw request body using the shared secret and comparing the results with a
 * constant-time comparison.
 *
 * <p>When no secret is configured on the endpoint, {@link #sign} returns {@code null}
 * and the header is omitted from the request.
 *
 * <p>This is the default {@link SignatureStrategy} registered by the library's
 * auto-configuration. Override the {@code webhookSignatureStrategy} bean to use a
 * different algorithm.
 */
public class HmacSha256SignatureStrategy implements SignatureStrategy {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER = "X-Webhook-Signature";
    private static final HexFormat HEX = HexFormat.of();

    /**
     * Computes an HMAC-SHA256 signature over the request body.
     *
     * <p>Returns {@code null} when {@code secret} is {@code null} or blank, which
     * causes the caller to omit the signature header entirely.
     *
     * @param body   the serialised JSON request body; must not be {@code null}
     * @param secret the HMAC secret configured on the endpoint; may be {@code null}
     * @return the signature string in the form {@code sha256=<hex>}, or {@code null}
     *         when no secret is present
     * @throws IllegalStateException if HmacSHA256 is not available in the JVM
     */
    @Override
    public String sign(String body, String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /**
     * Returns the name of the HTTP header used to carry the signature.
     *
     * @return {@code "X-Webhook-Signature"}
     */
    @Override
    public String headerName() {
        return HEADER;
    }
}