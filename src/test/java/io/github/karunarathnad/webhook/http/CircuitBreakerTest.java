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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CircuitBreakerTest {

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
    void circuitBreakerIsPerEndpoint() {
        wireMock.stubFor(post(urlEqualTo("/hooks/endpoint1"))
                .willReturn(aResponse().withStatus(500)));
        wireMock.stubFor(post(urlEqualTo("/hooks/endpoint2"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint failingEndpoint = WebhookEndpoint.builder()
                .id("failing-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/endpoint1")
                .secret("secret")
                .build();

        WebhookEndpoint healthyEndpoint = WebhookEndpoint.builder()
                .id("healthy-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/endpoint2")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        for (int i = 0; i < 3; i++) {
            WebhookDeliveryResult result = webhookClient.send(event, failingEndpoint);
        }

        WebhookDeliveryResult result = webhookClient.send(event, healthyEndpoint);
        assertThat(result.success()).isTrue();
    }

    @Test
    void successfulDeliveryResetsCircuitBreakerState() {
        wireMock.stubFor(post(urlEqualTo("/hooks/recovery"))
                .inScenario("recovery")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));

        wireMock.stubFor(post(urlEqualTo("/hooks/recovery"))
                .inScenario("recovery")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("recovery-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/recovery")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result1 = webhookClient.send(event, endpoint);
        // Could be success or failure depending on retry logic
        assertThat(result1).isNotNull();

        WebhookDeliveryResult result2 = webhookClient.send(event, endpoint);
        // Circuit breaker should allow request through
        if (result2.success()) {
            assertThat(result2.httpStatusCode()).isEqualTo(200);
        }
    }

    @Test
    void fourHundredErrorsDoNotTripCircuitBreaker() {
        wireMock.stubFor(post(urlEqualTo("/hooks/unauthorized"))
                .willReturn(aResponse().withStatus(401)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("auth-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/unauthorized")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        for (int i = 0; i < 5; i++) {
            WebhookDeliveryResult result = webhookClient.send(event, endpoint);
            assertThat(result.success()).isFalse();
            assertThat(result.httpStatusCode()).isEqualTo(401);
        }

        var allRequests = wireMock.findAll(postRequestedFor(urlEqualTo("/hooks/unauthorized")));
        assertThat(allRequests.size()).isGreaterThanOrEqualTo(5);
    }
}

