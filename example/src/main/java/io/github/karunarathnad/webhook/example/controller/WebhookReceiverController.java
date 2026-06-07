package io.github.karunarathnad.webhook.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Simulates a downstream service that receives webhooks.
 * In a real integration this would live in a separate service.
 *
 * The library sends signed requests with an X-Webhook-Signature header
 * in the format: sha256=<hex-digest>
 * Validate this signature on the receiver side using the shared secret.
 */
@RestController
@RequestMapping("/receive")
public class WebhookReceiverController {

    private static final Logger log = LoggerFactory.getLogger(WebhookReceiverController.class);

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

        // TODO: verify the signature against the shared secret before trusting the payload.
        //   String expected = "sha256=" + HmacUtils.hmacSha256Hex(secret, payload);
        //   if (!MessageDigest.isEqual(expected.getBytes(), signature.getBytes())) throw ...

        return ResponseEntity.ok().build();
    }
}
