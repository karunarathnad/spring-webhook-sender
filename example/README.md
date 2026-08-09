# spring-webhook-sender — Example Application

A runnable Spring Boot application that demonstrates every major feature of
[spring-webhook-sender](../README.md) through a simple order-management scenario.

## What this example shows

| Feature | Where to look |
|---|---|
| Adding the dependency | `pom.xml` |
| Configuring endpoints (signed, unsigned, filtered, custom headers) | `WebhookConfig.java` |
| Injecting and using `WebhookClient` | `OrderService.java` |
| Blocking vs. async delivery | `OrderService.createOrder()` |
| Event-type subscription filtering | `analyticsEndpoint` bean |
| Custom delivery listener | `OrderDeliveryListener.java` |
| Tuning retry, circuit breaker, and thread pool | `application.yml` |
| Receiving and verifying a signed webhook (HMAC-SHA256, constant-time compare, 401 on mismatch) | `WebhookReceiverController.java` |

## How it works

The app runs a single Spring Boot process on port 8080. When you create an order
via the REST API, the library sends webhooks to two in-process endpoints:

```
POST /orders
  │
  ├─► primary-endpoint  (signed, subscribes to all order.*)   ──► POST /receive/webhooks
  │     blocking send (webhookClient.send)
  │
  └─► analytics-endpoint  (unsigned, only order.created)      ──► POST /receive/webhooks
        non-blocking send (webhookClient.sendAsync)
```

The **analytics endpoint** only subscribes to `order.created`, so update and
cancel events are automatically skipped — no code needed on your side.

## Prerequisites

- Java 21+
- Maven 3.9+

## Run

```bash
cd example
mvn spring-boot:run
```

## Try it out

### 1 — Create an order (triggers `order.created`)

```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","product":"Laptop","amount":1299.99}' | jq
```

Watch the console — you will see:

- The library signing and dispatching to both endpoints
- `[DELIVERED]` lines from the custom `OrderDeliveryListener`
- `--- Webhook received ---` with the `X-Webhook-Signature` header printed by the receiver
- The receiver recomputing the HMAC over the raw body and accepting the request (200) because it matches
- Per-attempt audit log entries on the `webhook.audit` logger

Both the sender (`WebhookConfig`) and receiver (`WebhookReceiverController`) read the
same secret from the `example.webhook-secret` property in `application.yml`. Change
that value (or send a request with a tampered signature) and the receiver responds
`401 Unauthorized` and logs `Signature mismatch — rejecting request` instead of
silently accepting it. The unsigned analytics endpoint has no signature header at all,
so the receiver skips verification for it, as documented in the main
[README's signature section](../README.md#verifying-the-signature-on-the-receiving-side).

### 2 — Update order status (triggers `order.updated`, analytics endpoint skips it)

```bash
curl -s -X PUT "http://localhost:8080/orders/ORD-001/status?status=SHIPPED"
```

Look for `skipped=true` in the analytics-endpoint log line.

### 3 — Cancel an order (triggers `order.cancelled`)

```bash
curl -s -X DELETE http://localhost:8080/orders/ORD-001
```

Both async sends fire; the analytics endpoint skips because it only subscribes
to `order.created`.

## Key code snippets

### Defining an endpoint

```java
WebhookEndpoint endpoint = WebhookEndpoint.builder()
    .id("my-endpoint")                          // used as circuit-breaker key
    .targetUrl("https://example.com/hooks")
    .secret("whsec_…")                          // omit for unsigned delivery
    .subscribedEventTypes(Set.of("order.created")) // omit to receive all types
    .headers(Map.of("X-Api-Key", "key-xyz"))    // custom headers (optional), or .header(k, v) one at a time
    .build();
```

### Sending a webhook

```java
// Blocking
WebhookDeliveryResult result = webhookClient.send(event, endpoint);

// Non-blocking
webhookClient.sendAsync(event, endpoint)
    .thenAccept(r -> log.info("delivered={}", r.success()));
```

### Listening to delivery outcomes

```java
@Component
public class MyListener implements WebhookDeliveryListener {

    @Override
    public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        // persist to DB, emit metrics, etc.
    }

    @Override
    public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
        // dead-letter store, alert on-call, etc.
    }
}
```
