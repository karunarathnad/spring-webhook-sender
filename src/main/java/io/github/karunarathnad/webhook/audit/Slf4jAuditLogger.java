package io.github.karunarathnad.webhook.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link AuditLogger} registered by the library's auto-configuration.
 *
 * <p>Writes one structured log line per delivery attempt to the {@code webhook.audit}
 * SLF4J logger — {@code INFO} on success, {@code WARN} on failure. Override the
 * {@code webhookAuditLogger} bean to persist records elsewhere instead.
 */
public class Slf4jAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("webhook.audit");

    @Override
    public void log(WebhookAuditRecord record) {
        String msg = "webhook_delivery eventId={} eventType={} endpointId={} targetUrl={} " +
                     "httpStatus={} success={} attempt={} durationMs={} errorMessage={}";

        if (record.success()) {
            log.info(msg,
                    record.eventId(), record.eventType(), record.endpointId(),
                    record.targetUrl(), record.httpStatusCode(), true,
                    record.attemptNumber(), record.durationMs(), "-");
        } else {
            log.warn(msg,
                    record.eventId(), record.eventType(), record.endpointId(),
                    record.targetUrl(), record.httpStatusCode(), false,
                    record.attemptNumber(), record.durationMs(), record.errorMessage());
        }
    }
}