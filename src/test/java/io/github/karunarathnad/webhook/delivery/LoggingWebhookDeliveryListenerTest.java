package io.github.karunarathnad.webhook.delivery;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingWebhookDeliveryListenerTest {

    private final LoggingWebhookDeliveryListener listener = new LoggingWebhookDeliveryListener();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LoggingWebhookDeliveryListener.class);
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    void onSuccessLogsAtInfoWithAttemptsAndDuration() {
        WebhookEvent event = WebhookEvent.builder()
                .eventId("evt-1")
                .eventType("order.created")
                .payload(Map.of())
                .build();
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("billing-service")
                .targetUrl("https://billing.example.com/hooks")
                .build();
        WebhookDeliveryResult result = WebhookDeliveryResult.success(
                event.eventId(), endpoint.id(), 200, 1, Duration.ofMillis(42));

        listener.onSuccess(event, endpoint, result);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logEvent = appender.list.get(0);
        assertThat(logEvent.getLevel()).isEqualTo(Level.INFO);
        assertThat(logEvent.getFormattedMessage())
                .contains("eventId=evt-1")
                .contains("eventType=order.created")
                .contains("endpointId=billing-service")
                .contains("attempts=1")
                .contains("durationMs=42");
    }

    @Test
    void onPermanentFailureLogsAtErrorWithHttpStatusAndErrorMessage() {
        WebhookEvent event = WebhookEvent.builder()
                .eventId("evt-2")
                .eventType("order.cancelled")
                .payload(Map.of())
                .build();
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("billing-service")
                .targetUrl("https://billing.example.com/hooks")
                .build();
        WebhookDeliveryResult result = WebhookDeliveryResult.failure(
                event.eventId(), endpoint.id(), 500, "Server error 500", 3, Duration.ofMillis(150));

        listener.onPermanentFailure(event, endpoint, result);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logEvent = appender.list.get(0);
        assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(logEvent.getFormattedMessage())
                .contains("eventId=evt-2")
                .contains("eventType=order.cancelled")
                .contains("endpointId=billing-service")
                .contains("attempts=3")
                .contains("httpStatus=500")
                .contains("error=Server error 500");
    }
}
