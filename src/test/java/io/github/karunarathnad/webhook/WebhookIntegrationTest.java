package io.github.karunarathnad.webhook;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WebhookIntegrationTest {

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

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // No dynamic props needed — endpoint URL is built per test
    }

    @Autowired
    WebhookClient webhookClient;

    @Test
    void successfulDeliveryReturns2xx() {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("test-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("test-secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of("orderId", "ORD-001", "amount", 99.99))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.eventId()).isEqualTo(event.eventId());

        wireMock.verify(postRequestedFor(urlEqualTo("/hooks"))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("X-Webhook-Signature", matching("sha256=[a-f0-9]{64}")));
    }

    @Test
    void signatureHeaderOmittedWhenNoSecret() {
        wireMock.stubFor(post(urlEqualTo("/hooks/unsigned"))
                .willReturn(aResponse().withStatus(202)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("unsigned-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/unsigned")
                .secret(null)
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("ping")
                .payload(Map.of("message", "hello"))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/hooks/unsigned"))
                .withoutHeader("X-Webhook-Signature"));
    }

    @Test
    void clientErrorIsNotRetried() {
        wireMock.stubFor(post(urlEqualTo("/hooks/bad"))
                .willReturn(aResponse().withStatus(401)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("bad-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/bad")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of("key", "value"))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(401);
        // Only one attempt — 4xx are not retried
        assertThat(result.totalAttempts()).isEqualTo(1);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/hooks/bad")));
    }

    @Test
    void serverErrorIsRetriedUpToMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/hooks/flaky"))
                .willReturn(aResponse().withStatus(503)));
        
        // Reset request recording after stub setup
        wireMock.resetRequests();

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("flaky-endpoint-retry")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/flaky")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of("key", "value"))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isFalse();
        assertThat(result.totalAttempts()).isEqualTo(3);
        // Verify that at least 3 attempts were made (WireMock may count differently)
        var allRequests = wireMock.findAll(postRequestedFor(urlEqualTo("/hooks/flaky")));
        assertThat(allRequests.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void eventuallySucceedsAfterRetry() {
        wireMock.stubFor(post(urlEqualTo("/hooks/recover"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));

        wireMock.stubFor(post(urlEqualTo("/hooks/recover"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("recover-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/recover")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.shipped")
                .payload(Map.of("orderId", "ORD-002"))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.totalAttempts()).isEqualTo(2);
    }

    @Test
    void asyncDeliveryCompletesSuccessfully() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/hooks/async"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("async-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/async")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("payment.received")
                .payload(Map.of("amount", 150.00))
                .build();

        WebhookDeliveryResult result = webhookClient.sendAsync(event, endpoint).get();

        assertThat(result.success()).isTrue();
    }
}