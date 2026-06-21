package io.github.karunarathnad.webhook.delivery;

import io.github.karunarathnad.webhook.WebhookTestApplication;
import io.github.karunarathnad.webhook.core.WebhookClient;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {WebhookTestApplication.class, DeliveryListenerCallbacksTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeliveryListenerCallbacksTest {

    static WireMockServer wireMock;
    static List<DeliveryEvent> deliveryEvents = new ArrayList<>();

    static class DeliveryEvent {
        enum Type { SUCCESS, FAILURE }
        Type type;
        WebhookEvent event;
        WebhookEndpoint endpoint;
        WebhookDeliveryResult result;

        DeliveryEvent(Type type, WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
            this.type = type;
            this.event = event;
            this.endpoint = endpoint;
            this.result = result;
        }
    }

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
    void reset() {
        wireMock.resetAll();
        deliveryEvents.clear();
    }

    @AfterEach
    void cleanup() {
        deliveryEvents.clear();
    }

    @Autowired
    private WebhookClient webhookClient;

    @TestConfiguration
    static class TestConfig {
        @Bean
        WebhookDeliveryListener testDeliveryListener() {
            return new WebhookDeliveryListener() {
                @Override
                public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
                    deliveryEvents.add(new DeliveryEvent(DeliveryEvent.Type.SUCCESS, event, endpoint, result));
                }

                @Override
                public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
                    deliveryEvents.add(new DeliveryEvent(DeliveryEvent.Type.FAILURE, event, endpoint, result));
                }
            };
        }
    }

    @Test
    void invokesOnSuccessCallback() {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("success-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of("orderId", "ORD-001"))
                .build();

        webhookClient.send(event, endpoint);

        assertThat(deliveryEvents).hasSize(1);
        assertThat(deliveryEvents.get(0).type).isEqualTo(DeliveryEvent.Type.SUCCESS);
        assertThat(deliveryEvents.get(0).event.eventType()).isEqualTo("order.created");
        assertThat(deliveryEvents.get(0).endpoint.id()).isEqualTo("success-endpoint");
        assertThat(deliveryEvents.get(0).result.success()).isTrue();
    }

    @Test
    void invokesOnPermanentFailureCallback() {
        wireMock.stubFor(post(urlEqualTo("/hooks/fail"))
                .willReturn(aResponse().withStatus(401)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("fail-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/fail")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        webhookClient.send(event, endpoint);

        assertThat(deliveryEvents).hasSize(1);
        assertThat(deliveryEvents.get(0).type).isEqualTo(DeliveryEvent.Type.FAILURE);
        assertThat(deliveryEvents.get(0).result.success()).isFalse();
    }

    @Test
    void onSuccessContainsCorrectData() {
        wireMock.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(202)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("callback-test")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("payment.received")
                .payload(Map.of("amount", 500.0))
                .build();

        webhookClient.send(event, endpoint);

        assertThat(deliveryEvents).hasSize(1);
        DeliveryEvent de = deliveryEvents.get(0);
        assertThat(de.result.eventId()).isEqualTo(event.eventId());
        assertThat(de.result.endpointId()).isEqualTo(endpoint.id());
        assertThat(de.result.httpStatusCode()).isEqualTo(202);
        assertThat(de.result.totalAttempts()).isGreaterThan(0);
    }

    @Test
    void onPermanentFailureContainsErrorMessage() {
        wireMock.stubFor(post(urlEqualTo("/hooks/error"))
                .willReturn(aResponse().withStatus(500)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("error-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/error")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        webhookClient.send(event, endpoint);

        assertThat(deliveryEvents).hasSize(1);
        DeliveryEvent de = deliveryEvents.get(0);
        assertThat(de.result.errorMessage()).isNotNull();
        assertThat(de.result.errorMessage()).isNotEmpty();
    }
}

