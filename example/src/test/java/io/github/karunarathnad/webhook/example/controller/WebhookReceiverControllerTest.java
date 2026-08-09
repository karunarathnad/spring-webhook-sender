package io.github.karunarathnad.webhook.example.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookReceiverController.class)
@TestPropertySource(properties = "example.webhook-secret=test-secret")
class WebhookReceiverControllerTest {

    private static final String SECRET = "test-secret";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsRequestWithValidSignature() throws Exception {
        String payload = "{\"eventType\":\"order.created\"}";
        String signature = sign(payload, SECRET);

        mockMvc.perform(post("/receive/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRequestWithTamperedSignature() throws Exception {
        String payload = "{\"eventType\":\"order.created\"}";
        String wrongSignature = sign(payload, "some-other-secret");

        mockMvc.perform(post("/receive/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", wrongSignature)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsUnsignedRequestWithoutSignatureHeader() throws Exception {
        mockMvc.perform(post("/receive/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\"}"))
                .andExpect(status().isOk());
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(digest);
    }
}
