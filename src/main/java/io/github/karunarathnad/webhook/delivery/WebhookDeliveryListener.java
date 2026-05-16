package io.github.karunarathnad.webhook.delivery;

import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;

/**
 * Callback interface for reacting to the final outcome of a webhook delivery.
 *
 * <p>Both methods are called once per delivery, after all retry attempts have finished.
 * They provide a natural hook for application-specific concerns such as recording
 * delivery metrics, sending failure alerts, or writing failed events to a dead-letter
 * store.
 *
 * <p>Register a custom listener by declaring a Spring bean that implements this interface.
 * The library's auto-configuration backs off when such a bean is present, so only one
 * implementation is active at a time:
 *
 * <pre>{@code
 * @Bean
 * public WebhookDeliveryListener webhookDeliveryListener(AlertService alerts) {
 *     return new WebhookDeliveryListener() {
 *
 *         @Override
 *         public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint,
 *                               WebhookDeliveryResult result) {
 *             metrics.increment("webhook.delivered", "endpoint", endpoint.id());
 *         }
 *
 *         @Override
 *         public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint,
 *                                        WebhookDeliveryResult result) {
 *             alerts.send("Delivery failed for " + endpoint.id()
 *                     + " after " + result.totalAttempts() + " attempts");
 *         }
 *     };
 * }
 * }</pre>
 *
 * <p>Both methods have default no-op implementations so that partial implementations
 * only need to override the callbacks they care about.
 *
 * <p>The default listener registered by the library is
 * {@link LoggingWebhookDeliveryListener}, which logs successes at INFO level and
 * permanent failures at ERROR level.
 */
public interface WebhookDeliveryListener {

    /**
     * Called when a webhook event was delivered successfully.
     *
     * <p>The result is guaranteed to have {@code success() == true}. The
     * {@code totalAttempts} field reflects how many tries were needed, which may
     * be greater than one if earlier attempts encountered transient errors.
     *
     * @param event    the event that was delivered
     * @param endpoint the endpoint that accepted the event
     * @param result   the delivery outcome, including attempt count and duration
     */
    default void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {}

    /**
     * Called when all retry attempts were exhausted without a successful delivery.
     *
     * <p>The result is guaranteed to have {@code success() == false} and
     * {@code skipped() == false}. The {@code errorMessage} and {@code httpStatusCode}
     * fields carry details about the last failure. Use this callback to trigger
     * alerts, write to a dead-letter queue, or schedule a manual retry.
     *
     * @param event    the event that could not be delivered
     * @param endpoint the endpoint that rejected or did not respond to the event
     * @param result   the delivery outcome, including the error reason and attempt count
     */
    default void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {}
}