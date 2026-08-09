package io.github.karunarathnad.webhook.delivery;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a bug where an exception thrown by a custom
 * {@link WebhookDeliveryListener} corrupted the already-computed delivery outcome:
 * {@code onSuccess} was called inside the same try block whose generic catch turned
 * any exception into a reported permanent failure, so a listener bug could make an
 * actually-successful delivery come back as {@code success() == false}. An exception
 * from the listener also must not propagate out of {@code send()}, which is documented
 * to never throw.
 */
@SpringBootTest(classes = {WebhookTestApplication.class, ThrowingListenerRegressionTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ThrowingListenerRegressionTest {

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

    @TestConfiguration
    static class TestConfig {
        @Bean
        WebhookDeliveryListener throwingDeliveryListener() {
            return new WebhookDeliveryListener() {
                @Override
                public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
                    throw new RuntimeException("boom — simulated bug in a custom listener");
                }

                @Override
                public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
                    throw new RuntimeException("boom — simulated bug in a custom listener");
                }
            };
        }
    }

    @Test
    void listenerExceptionOnSuccessDoesNotCorruptTheReportedResult() {
        wireMock.stubFor(post(urlEqualTo("/hooks/throwing-listener-success"))
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("throwing-listener-success")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/throwing-listener-success")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        // If the listener's exception escaped send() uncaught, this call itself would
        // throw and fail the test before the assertions below even run.
        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
    }

    @Test
    void listenerExceptionOnPermanentFailureDoesNotEscapeSend() {
        wireMock.stubFor(post(urlEqualTo("/hooks/throwing-listener-failure"))
                .willReturn(aResponse().withStatus(401)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("throwing-listener-failure")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/throwing-listener-failure")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(401);
    }
}
