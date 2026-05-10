package io.github.karunarathnad.webhook.audit;

public interface AuditLogger {

    void log(WebhookAuditRecord record);
}
