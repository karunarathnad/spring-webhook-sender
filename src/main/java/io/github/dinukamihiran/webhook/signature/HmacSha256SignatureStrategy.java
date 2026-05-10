package io.github.karunarathnad.webhook.signature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Header value format: "sha256=<hex>" — same convention as GitHub and Stripe webhooks
public class HmacSha256SignatureStrategy implements SignatureStrategy {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER = "X-Webhook-Signature";
    private static final HexFormat HEX = HexFormat.of();

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

    @Override
    public String headerName() {
        return HEADER;
    }
}
