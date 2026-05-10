package io.github.karunarathnad.webhook.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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