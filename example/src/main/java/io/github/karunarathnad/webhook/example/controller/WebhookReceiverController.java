package io.github.karunarathnad.webhook.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Simulates a downstream service that receives webhooks.
 * In a real integration this would live in a separate service.
 *
 * The library sends signed requests with an X-Webhook-Signature header
 * in the format: sha256=<hex-digest>. This controller verifies that signature
 * using the same shared secret configured on the sending side (see WebhookConfig),
 * following the approach documented in the library's README under
 * "Verifying the signature on the receiving side".
 */
@RestController
@RequestMapping("/receive")
public class WebhookReceiverController {

    private static final Logger log = LoggerFactory.getLogger(WebhookReceiverController.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final String webhookSecret;

    /**
     * @param webhookSecret the shared HMAC secret used to verify incoming signatures;
     *                      must match the secret configured on the sending side
     */
    public WebhookReceiverController(@Value("${example.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * Accepts an incoming webhook, logs its headers and payload, and verifies the
     * {@code X-Webhook-Signature} header when present.
     *
     * @param payload the raw request body, verbatim so the signature can be recomputed
     * @param headers the request headers, inspected for the signature and any custom
     *                headers configured on the sending endpoint
     * @return 200 OK if unsigned or the signature is valid; 401 Unauthorized if a
     *         signature was supplied but does not match
     */
    @PostMapping("/webhooks")
    public ResponseEntity<Void> receive(
            @RequestBody String payload,
            @RequestHeader HttpHeaders headers) {

        String signature = headers.getFirst("X-Webhook-Signature");
        String apiKey    = headers.getFirst("X-Api-Key");
        String source    = headers.getFirst("X-Source");

        log.info("--- Webhook received ---");
        log.info("  Signature : {}", signature != null ? signature : "(unsigned)");
        if (apiKey != null) log.info("  X-Api-Key : {}", apiKey);
        if (source != null) log.info("  X-Source  : {}", source);
        log.info("  Payload   : {}", payload);

        // The analytics endpoint (see WebhookConfig) is intentionally unsigned to
        // demonstrate the omit-secret path, so only verify when a signature is present.
        if (signature != null && !isValidSignature(payload, signature)) {
            log.warn("  Signature mismatch — rejecting request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to compute expected signature", e);
            return false;
        }
    }
}
