package io.github.karunarathnad.webhook.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a leak where {@code send()} attached a new {@code onRetry}
 * listener to the registry-cached {@link io.github.resilience4j.retry.Retry} instance
 * on every call, so a second call to the same endpoint replayed the first call's
 * retry log lines (and pinned its event objects in memory) on every subsequent retry.
 */
@SpringBootTest(classes = WebhookTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RetryLoggingRegressionTest {

    static WireMockServer wireMock;

    Logger senderLogger;
    ListAppender<ILoggingEvent> appender;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    WebhookClient webhookClient;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        senderLogger = (Logger) LoggerFactory.getLogger(WebhookHttpSender.class);
        senderLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        senderLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        senderLogger.detachAppender(appender);
        senderLogger.setLevel(null);
    }

    @Test
    void secondCallDoesNotReplayFirstCallsRetryLogLines() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("leak-check-endpoint")
                .targetUrl("http://localhost:" + wireMock.port() + "/hooks/leak-check")
                .secret("secret")
                .build();

        stubFailTwiceThenSucceed("round-1");
        WebhookEvent firstEvent = WebhookEvent.builder().eventType("test.event").payload(Map.of()).build();
        WebhookDeliveryResult firstResult = webhookClient.send(firstEvent, endpoint);
        assertThat(firstResult.success()).isTrue();
        assertThat(firstResult.totalAttempts()).isEqualTo(3);

        // Everything captured so far belongs to the first call; only what's logged
        // from here on should reflect the second call.
        appender.list.clear();
        wireMock.resetAll();
        stubFailTwiceThenSucceed("round-2");

        WebhookEvent secondEvent = WebhookEvent.builder().eventType("test.event").payload(Map.of()).build();
        WebhookDeliveryResult secondResult = webhookClient.send(secondEvent, endpoint);
        assertThat(secondResult.success()).isTrue();
        assertThat(secondResult.totalAttempts()).isEqualTo(3);

        List<String> retryMessages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Retrying webhook"))
                .toList();

        // 3 attempts means exactly 2 retries. A leaked listener from the first call
        // would double this count and would also re-log the first event's id.
        assertThat(retryMessages).hasSize(2);
        assertThat(retryMessages).allMatch(message -> message.contains(secondEvent.eventId()));
        assertThat(retryMessages).noneMatch(message -> message.contains(firstEvent.eventId()));
    }

    private void stubFailTwiceThenSucceed(String scenario) {
        wireMock.stubFor(post(urlEqualTo("/hooks/leak-check"))
                .inScenario(scenario)
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("failed-once"));

        wireMock.stubFor(post(urlEqualTo("/hooks/leak-check"))
                .inScenario(scenario)
                .whenScenarioStateIs("failed-once")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("failed-twice"));

        wireMock.stubFor(post(urlEqualTo("/hooks/leak-check"))
                .inScenario(scenario)
                .whenScenarioStateIs("failed-twice")
                .willReturn(aResponse().withStatus(200)));
    }
}
