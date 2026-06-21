package io.github.karunarathnad.webhook.http;

import io.github.karunarathnad.webhook.WebhookTestApplication;
import io.github.karunarathnad.webhook.core.WebhookClient;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "webhook.max-payload-size-bytes=1000")
class PayloadSizeValidationTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired
    private WebhookClient webhookClient;

    @Test
    void rejectsOversizedPayload() {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("size-test")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .build();

        Map<String, Object> largePayload = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largePayload.put("field" + i, "x".repeat(20));
        }

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.oversized")
                .payload(largePayload)
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("exceeds maximum");
        assertThat(result.httpStatusCode()).isEqualTo(-1);
        wireMock.verify(0, postRequestedFor(urlEqualTo("/hooks")));
    }

    @Test
    void allowsPayloadWithinLimit() {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("size-ok")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .build();

        Map<String, String> smallPayload = Map.of(
                "orderId", "ORD-123",
                "amount", "99.99"
        );

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(smallPayload)
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/hooks")));
    }

    @Test
    void payloadSizeCheckHappensBeforeHttpRequest() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("size-check-first")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .build();

        Map<String, Object> largePayload = new HashMap<>();
        for (int i = 0; i < 150; i++) {
            largePayload.put("field" + i, "value");
        }

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.large")
                .payload(largePayload)
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("exceeds", "size");
        wireMock.verify(0, postRequestedFor(anyUrl()));
    }
}

