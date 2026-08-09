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

/**
 * Regression test for a bug where a successful delivery's {@code totalDuration} only
 * measured the final (successful) attempt, not the time spent across all attempts —
 * contradicting {@link WebhookDeliveryResult#success}'s documented contract. Failure
 * results already measured the full elapsed time; only the success path was wrong.
 */
@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SuccessDurationRegressionTest {

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
    void successfulDeliveryDurationIncludesTimeSpentOnEarlierFailedAttempts() {
        wireMock.stubFor(post(urlEqualTo("/hooks/eventual-success"))
                .inScenario("eventual-success")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));

        wireMock.stubFor(post(urlEqualTo("/hooks/eventual-success"))
                .inScenario("eventual-success")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("eventual-success-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/eventual-success")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of())
                .build();

        WebhookDeliveryResult result = webhookClient.send(event, endpoint);

        assertThat(result.success()).isTrue();
        assertThat(result.totalAttempts()).isEqualTo(2);

        // Test config uses a 50ms initial retry interval (src/test/resources/application.yml).
        // A duration measuring only the final, fast attempt would be a few ms at most;
        // the correct total must be at least the backoff wait between attempt 1 and 2.
        assertThat(result.totalDuration().toMillis()).isGreaterThanOrEqualTo(40);
    }
}
