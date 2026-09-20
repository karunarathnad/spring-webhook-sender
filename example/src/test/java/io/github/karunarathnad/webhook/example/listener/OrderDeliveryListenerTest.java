package io.github.karunarathnad.webhook.example.listener;

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

class OrderDeliveryListenerTest {

    private final OrderDeliveryListener listener = new OrderDeliveryListener();
    private final WebhookEndpoint endpoint = WebhookEndpoint.builder()
            .id("primary-endpoint")
            .targetUrl("http://localhost:8080/receive/webhooks")
            .build();
    private final WebhookEvent event = WebhookEvent.builder()
            .eventType("order.created")
            .payload(Map.of("orderId", "order-1"))
            .build();

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(OrderDeliveryListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void onSuccess_logsDeliveredAtInfoLevel_withEventAndEndpointDetails() {
        WebhookDeliveryResult result = WebhookDeliveryResult.success(
                event.eventId(), endpoint.id(), 200, 1, Duration.ofMillis(50));

        listener.onSuccess(event, endpoint, result);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logged = appender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.INFO);
        assertThat(logged.getFormattedMessage())
                .contains("order.created")
                .contains("primary-endpoint");
    }

    @Test
    void onPermanentFailure_logsFailureAtErrorLevel_withEventAndEndpointDetails() {
        WebhookDeliveryResult result = WebhookDeliveryResult.failure(
                event.eventId(), endpoint.id(), 500, "connection refused", 3, Duration.ofSeconds(7));

        listener.onPermanentFailure(event, endpoint, result);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logged = appender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.ERROR);
        assertThat(logged.getFormattedMessage())
                .contains("order.created")
                .contains("primary-endpoint")
                .contains("connection refused");
    }

}
