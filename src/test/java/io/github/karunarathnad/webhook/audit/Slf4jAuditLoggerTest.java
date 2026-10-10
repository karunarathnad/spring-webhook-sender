package io.github.karunarathnad.webhook.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Slf4jAuditLoggerTest {

    private final Slf4jAuditLogger auditLogger = new Slf4jAuditLogger();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger("webhook.audit");
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
    void logsSuccessfulDeliveryAtInfo() {
        auditLogger.log(recordFor(true, 200, null));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("success=true")
                .contains("httpStatus=200")
                .contains("errorMessage=-");
    }

    @Test
    void logsFailedDeliveryAtWarnWithErrorMessage() {
        auditLogger.log(recordFor(false, 500, "Server error 500"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("success=false")
                .contains("httpStatus=500")
                .contains("errorMessage=Server error 500");
    }

    private WebhookAuditRecord recordFor(boolean success, int httpStatus, String errorMessage) {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id("billing-service")
                .targetUrl("https://billing.example.com/hooks")
                .build();
        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(Map.of())
                .build();
        return WebhookAuditRecord.of(event, endpoint, httpStatus, success, errorMessage, 1, 10L);
    }
}
