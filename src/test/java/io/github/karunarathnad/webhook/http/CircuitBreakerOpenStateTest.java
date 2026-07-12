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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The base test config keeps {@code minimum-number-of-calls} high (100) so the circuit
 * stays closed in unrelated tests. This class lowers it just enough to force the OPEN
 * state deterministically within a handful of calls.
 */
@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "webhook.circuit-breaker.minimum-number-of-calls=4",
        "webhook.circuit-breaker.sliding-window-size=4"
})
class CircuitBreakerOpenStateTest {

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
    WebhookClient webhookClient;

    @Test
    void openCircuitRejectsCallsWithoutContactingTheEndpoint() {
        wireMock.stubFor(post(urlEqualTo("/hooks/always-down"))
                .willReturn(aResponse().withStatus(500)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("always-down-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/always-down")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        // Enough failing calls to fill the sliding window and trip the breaker.
        for (int i = 0; i < 4; i++) {
            WebhookDeliveryResult result = webhookClient.send(event, endpoint);
            assertThat(result.success()).isFalse();
        }

        int requestsBeforeOpen = wireMock.findAll(postRequestedFor(urlEqualTo("/hooks/always-down"))).size();
        assertThat(requestsBeforeOpen).isGreaterThan(0);

        WebhookDeliveryResult rejected = webhookClient.send(event, endpoint);

        assertThat(rejected.success()).isFalse();
        assertThat(rejected.totalAttempts()).isZero();
        assertThat(rejected.httpStatusCode()).isEqualTo(-1);
        assertThat(rejected.errorMessage()).containsIgnoringCase("does not permit further calls");

        int requestsAfterOpen = wireMock.findAll(postRequestedFor(urlEqualTo("/hooks/always-down"))).size();
        assertThat(requestsAfterOpen)
                .as("no HTTP call should have been made once the circuit is open")
                .isEqualTo(requestsBeforeOpen);
    }

    @Test
    void fourHundredErrorsAloneDoNotOpenTheCircuit() {
        wireMock.stubFor(post(urlEqualTo("/hooks/unauthorized"))
                .willReturn(aResponse().withStatus(401)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("unauthorized-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/unauthorized")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        for (int i = 0; i < 6; i++) {
            WebhookDeliveryResult result = webhookClient.send(event, endpoint);
            assertThat(result.success()).isFalse();
            assertThat(result.httpStatusCode()).isEqualTo(401);
        }

        // NonRetryable 4xx failures aren't counted by the circuit breaker, so every
        // call should still reach the endpoint instead of being short-circuited.
        int requests = wireMock.findAll(postRequestedFor(urlEqualTo("/hooks/unauthorized"))).size();
        assertThat(requests).isEqualTo(6);
    }
}
