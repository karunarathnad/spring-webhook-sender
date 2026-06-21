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
class CustomHeadersIntegrationTest {

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
    void sendsCustomHeaders() {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("custom-headers")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .header("X-Tenant-ID", "tenant-123")
                .header("X-Source-System", "order-service")
                .header("X-API-Version", "v2")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of("orderId", "ORD-001"))
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/hooks"))
                .withHeader("X-Tenant-ID", equalTo("tenant-123"))
                .withHeader("X-Source-System", equalTo("order-service"))
                .withHeader("X-API-Version", equalTo("v2")));
    }

    @Test
    void customHeadersPreservedAcrossRetries() {
        wireMock.stubFor(post(urlEqualTo("/hooks/retry"))
                .inScenario("retry-headers")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("success"));

        wireMock.stubFor(post(urlEqualTo("/hooks/retry"))
                .inScenario("retry-headers")
                .whenScenarioStateIs("success")
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("retry-with-headers")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/retry")
                .secret("secret")
                .header("X-Custom-Header", "custom-value")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        wireMock.verify(2, postRequestedFor(urlEqualTo("/hooks/retry"))
                .withHeader("X-Custom-Header", equalTo("custom-value")));
    }

    @Test
    void endpointWithoutCustomHeadersWorks() {
        wireMock.stubFor(post(urlEqualTo("/hooks/plain"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("no-headers")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/plain")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/hooks/plain")));
    }

    @Test
    void multipleEndpointsHaveIndependentHeaders() {
        wireMock.stubFor(post(urlEqualTo("/webhooks/endpoint1"))
                .willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(post(urlEqualTo("/webhooks/endpoint2"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint1 = WebhookEndpoint.builder()
                .id("endpoint-1")
                .targetUrl("http://localhost:" + wireMock.port() + "/webhooks/endpoint1")
                .secret("secret")
                .header("X-Tenant", "tenant-a")
                .build();

        WebhookEndpoint endpoint2 = WebhookEndpoint.builder()
                .id("endpoint-2")
                .targetUrl("http://localhost:" + wireMock.port() + "/webhooks/endpoint2")
                .secret("secret")
                .header("X-Tenant", "tenant-b")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        webhookClient.send(event, endpoint1);
        webhookClient.send(event, endpoint2);

        wireMock.verify(postRequestedFor(urlEqualTo("/webhooks/endpoint1"))
                .withHeader("X-Tenant", equalTo("tenant-a")));
        wireMock.verify(postRequestedFor(urlEqualTo("/webhooks/endpoint2"))
                .withHeader("X-Tenant", equalTo("tenant-b")));
    }
}

