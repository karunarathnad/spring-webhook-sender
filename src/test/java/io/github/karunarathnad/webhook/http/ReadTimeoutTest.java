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
 * Regression test for a bug where {@code webhook.http.read-timeout} was applied to the
 * HTTP connection-request timeout instead of the socket read timeout, leaving the actual
 * read timeout unbounded. A hanging endpoint would block the calling thread indefinitely
 * instead of failing fast.
 */
@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "webhook.http.read-timeout=300ms"
})
class ReadTimeoutTest {

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
    void hangingEndpointIsAbortedByReadTimeoutInsteadOfBlockingForever() {
        wireMock.stubFor(post(urlEqualTo("/hooks/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(6000)));

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("slow-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/slow")
                .secret("secret")
                .build();

        WebhookEvent event = WebhookEvent.builder()
                .eventType("test.event")
                .payload(Map.of("key", "value"))
                .build();

        long start = System.nanoTime();
        WebhookDeliveryResult result = webhookClient.send(event, endpoint);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatusCode()).isEqualTo(-1);
        // Each attempt should be aborted around the 300ms read-timeout rather than
        // blocking for the full 6s fixed delay stubbed above. Well under half the
        // stub delay leaves ample margin while still catching a regression to
        // unbounded reads.
        assertThat(elapsedMs).isLessThan(3000);
    }
}
