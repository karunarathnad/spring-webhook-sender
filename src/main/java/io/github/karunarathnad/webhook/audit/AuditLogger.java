package io.github.karunarathnad.webhook.audit;

/**
 * Records an audit entry for each individual webhook delivery attempt.
 *
 * <p>Unlike {@link io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener},
 * which is called once per event after all retries finish, this interface is invoked
 * after every HTTP attempt — including intermediate failures that will be retried.
 * It is therefore the right place to maintain a detailed, per-attempt delivery log.
 *
 * <p>The default implementation writes structured log lines to the {@code webhook.audit}
 * SLF4J logger. To persist records to a database instead, register a custom bean and
 * the auto-configured default will be skipped:
 *
 * <pre>{@code
 * @Bean
 * public AuditLogger webhookAuditLogger(AuditRepository repo) {
 *     return record -> repo.save(toEntity(record));
 * }
 * }</pre>
 */
public interface AuditLogger {

    /**
     * Records the outcome of a single delivery attempt.
     *
     * <p>The {@link WebhookAuditRecord} contains the event identifier, endpoint
     * details, HTTP status code, success flag, error message, attempt number,
     * and the time taken for that specific attempt.
     *
     * @param record the audit record for this attempt; never {@code null}
     */
    void log(WebhookAuditRecord record);
}