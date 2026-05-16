package io.github.karunarathnad.webhook.core;

import java.util.concurrent.CompletableFuture;

/**
 * The primary entry point for sending outgoing webhook events.
 *
 * <p>Implementations handle all aspects of delivery on behalf of the caller: payload
 * serialisation, HMAC-SHA256 signing, exponential-backoff retry, and per-endpoint
 * circuit breaking. Application code only needs to build a {@link WebhookEvent} and
 * a {@link WebhookEndpoint}, then hand them off through this interface.
 *
 * <p>The default implementation is registered automatically by Spring Boot
 * auto-configuration. Inject it wherever webhook delivery is needed:
 *
 * <pre>{@code
 * @Autowired
 * private WebhookClient webhookClient;
 *
 * webhookClient.sendAsync(event, endpoint)
 *         .thenAccept(result -> log.info("delivered={} attempts={}",
 *                 result.success(), result.totalAttempts()));
 * }</pre>
 *
 * <p>Prefer {@link #sendAsync} in request-handling code to avoid blocking the caller's
 * thread. Use {@link #send} in batch jobs or other contexts where blocking is acceptable.
 */
public interface WebhookClient {

    /**
     * Sends a webhook event to the given endpoint without blocking the calling thread.
     *
     * <p>The call returns a {@link CompletableFuture} immediately. Signing, HTTP dispatch,
     * retry, and circuit-breaker evaluation all happen on a background thread managed by
     * the library's internal thread pool.
     *
     * <p>If the endpoint has a non-empty {@code subscribedEventTypes} set that does not
     * contain the event's type, the future completes right away with a skipped result
     * and no HTTP request is made.
     *
     * @param event    the event to deliver; must not be {@code null}
     * @param endpoint the target endpoint configuration; must not be {@code null}
     * @return a future that completes with the final delivery outcome once all retry
     *         attempts have been exhausted; never {@code null}
     */
    CompletableFuture<WebhookDeliveryResult> sendAsync(WebhookEvent event, WebhookEndpoint endpoint);

    /**
     * Sends a webhook event to the given endpoint and blocks until the outcome is known.
     *
     * <p>Internally this calls {@link #sendAsync} and waits for the returned future to
     * complete. It is a convenience method for call sites such as batch processors or
     * command-line tools where blocking the current thread is not a concern. Avoid
     * calling it from a web request thread.
     *
     * @param event    the event to deliver; must not be {@code null}
     * @param endpoint the target endpoint configuration; must not be {@code null}
     * @return the delivery outcome after all attempts have finished; never {@code null}
     */
    WebhookDeliveryResult send(WebhookEvent event, WebhookEndpoint endpoint);
}