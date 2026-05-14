# spring-webhook-sender

[![Maven Central](https://img.shields.io/maven-central/v/io.github.karunarathnad/spring-webhook-sender)](https://central.sonatype.com/artifact/io.github.karunarathnad/spring-webhook-sender)

A lightweight Spring Boot 3.x library for sending outgoing webhooks. Drop it in as a dependency, inject `WebhookClient`, and fire events. The library takes care of the rest.

**Features:** HMAC-SHA256 signing · exponential-backoff retry · HTTP 429/Retry-After handling · per-endpoint circuit breaking · payload size validation · event-type subscription filtering · delivery lifecycle callbacks · secret generation and rotation · non-blocking async dispatch · structured audit logging.

Everything is wired up automatically via Spring Boot auto-configuration and tunable through `application.yml`.

## Requirements

- Java 17+
- Spring Boot 3.2+

## Installation

```xml
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>spring-webhook-sender</artifactId>
    <version>2.0.0</version>
</dependency>
```

No extra configuration is required. Spring Boot picks up the auto-configuration automatically.

## Basic usage

```java
@Service
public class OrderService {

    @Autowired
    private WebhookClient webhookClient;

    private static final WebhookEndpoint ORDER_ENDPOINT = WebhookEndpoint.builder()
            .id("order-service")
            .targetUrl("https://payments.example.com/webhooks")
            .secret(System.getenv("WEBHOOK_SECRET"))
            .build();

    public void onOrderCreated(Order order) {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(order)
                .metadata("source", "order-service")
                .build();

        // non-blocking — returns immediately
        webhookClient.sendAsync(event, ORDER_ENDPOINT)
                .thenAccept(result -> log.info("delivered={} attempts={}", result.success(), result.totalAttempts()));

        // or blocking, e.g. in a scheduled job
        // WebhookDeliveryResult result = webhookClient.send(event, ORDER_ENDPOINT);
    }
}
```

### Payload format

```json
{
  "eventId": "a3f1c2d4-...",
  "eventType": "order.created",
  "occurredAt": "2026-05-10T08:30:00Z",
  "metadata": { "source": "order-service" },
  "payload": { "orderId": "ORD-001", "amount": 99.99 }
}
```

The `X-Webhook-Signature` header is set to `sha256=<hmac>`. If no secret is configured on the endpoint, the header is omitted.

`WebhookEndpoint.id` is the circuit-breaker key — use a stable, unique value per destination. A tripped circuit on one endpoint does not affect others.

---

## Event-type subscription filtering

Restrict an endpoint to only receive specific event types. Unmatched events are skipped without making an HTTP call.

```java
WebhookEndpoint endpoint = WebhookEndpoint.builder()
        .id("billing-service")
        .targetUrl("https://billing.example.com/hooks")
        .secret(System.getenv("BILLING_WEBHOOK_SECRET"))
        .subscribedEventType("order.created")
        .subscribedEventType("order.cancelled")
        .build();
```

An empty `subscribedEventTypes` (the default) means the endpoint receives all events.

Skipped events return a `WebhookDeliveryResult` with `result.skipped() == true` and `result.success() == false`. No HTTP call is made and no retry is attempted.

---

## Retry behaviour

5xx responses and network errors are retried with exponential backoff. 4xx responses are **not** retried, except for **429 Too Many Requests**, which is retried and respects the `Retry-After` header.

| Response | Retried | Circuit breaker |
|----------|---------|-----------------|
| 2xx      | —       | counts as success |
| 429      | Yes (honors Retry-After) | not counted as failure |
| 4xx      | No      | not counted as failure |
| 5xx      | Yes     | counts as failure |
| Network error | Yes | counts as failure |

When a 429 response includes a `Retry-After` header (seconds or HTTP-date), the retry waits at least that long, capped at `webhook.retry.max-interval`.

---

## Secret management

Use `WebhookSecretManager` to generate new secrets or rotate an existing endpoint's secret without rebuilding the whole endpoint:

```java
@Autowired
private WebhookSecretManager secretManager;

// generate a new secret (whsec_<64-hex-chars>, cryptographically secure)
String secret = secretManager.generateSecret();

// rotate: returns a new WebhookEndpoint with a fresh secret, same id/url/subscriptions
WebhookEndpoint rotated = secretManager.rotateSecret(existingEndpoint);
```

Secrets are prefixed with `whsec_` and backed by 32 bytes of `SecureRandom`. Override the `WebhookSecretManager` bean to integrate with your own key store.

---

## Delivery lifecycle callbacks

Implement `WebhookDeliveryListener` to react to final delivery outcomes — hook in your own alerting, metrics, or dead-letter logic:

```java
@Bean
public WebhookDeliveryListener webhookDeliveryListener(AlertService alerts) {
    return new WebhookDeliveryListener() {

        @Override
        public void onSuccess(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
            metrics.increment("webhook.delivered", "endpoint", endpoint.id());
        }

        @Override
        public void onPermanentFailure(WebhookEvent event, WebhookEndpoint endpoint, WebhookDeliveryResult result) {
            alerts.send("Webhook permanently failed for " + endpoint.id()
                    + " after " + result.totalAttempts() + " attempts: " + result.errorMessage());
        }
    };
}
```

The default listener (`LoggingWebhookDeliveryListener`) logs successes at INFO and permanent failures at ERROR. `onPermanentFailure` is called only after all retry attempts are exhausted.

---

## Configuration

All settings are optional. The library ships with sensible defaults.

```yaml
webhook:
  max-payload-size-bytes: 262144  # 256 KB default; rejects oversized payloads before sending

  http:
    connect-timeout: 5s           # default 5s
    read-timeout: 10s             # default 10s

  retry:
    max-attempts: 3               # total attempts including first; set to 1 to disable. default 3
    initial-interval: 1s          # wait before the 2nd attempt. default 1s
    multiplier: 2.0               # wait doubles each retry (1s → 2s → 4s ...). default 2.0
    max-interval: 30s             # upper cap on retry wait, also caps Retry-After. default 30s

  circuit-breaker:
    failure-rate-threshold: 50             # open when this % of calls fail. default 50
    minimum-number-of-calls: 10            # minimum calls before evaluating rate. default 10
    sliding-window-size: 20                # default 20
    wait-duration-in-open-state: 30s       # default 30s
    permitted-calls-in-half-open-state: 3  # default 3

  async:
    core-pool-size: 4     # default 4
    max-pool-size: 16     # default 16
    queue-capacity: 1000  # events waiting for a thread; rejected when full. default 1000
    keep-alive: 60s       # default 60s
```

---

## Extending

### React to permanent failures (alerts, dead-letter queue)

Register a `WebhookDeliveryListener` bean — see [Delivery lifecycle callbacks](#delivery-lifecycle-callbacks) above.

### Persist audit records to a database

Each delivery attempt is logged to the `webhook.audit` SLF4J logger by default. To persist to a database instead, register your own `AuditLogger` bean:

```java
@Bean
public AuditLogger webhookAuditLogger(WebhookAuditRepository repo) {
    return record -> repo.save(toEntity(record));
}
```

`WebhookAuditRecord` contains: `eventId`, `eventType`, `endpointId`, `targetUrl`, `httpStatusCode`, `success`, `errorMessage`, `attemptNumber`, `durationMs`, `timestamp`.

### Custom signature strategy

```java
@Bean
public SignatureStrategy webhookSignatureStrategy() {
    return new MySignatureStrategy();
}
```

### Custom Jackson ObjectMapper

```java
@Bean("webhookObjectMapper")
public ObjectMapper webhookObjectMapper() {
    return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
}
```

---

## Verifying the signature on the receiving side

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(UTF_8)));
String received = request.getHeader("X-Webhook-Signature");

// constant-time comparison to prevent timing attacks
boolean valid = MessageDigest.isEqual(expected.getBytes(UTF_8), received.getBytes(UTF_8));
```

---

## Migrating from 1.x to 2.0

`WebhookEndpoint` gained a new `subscribedEventTypes` field. This changes the record's canonical constructor — a binary-incompatible change.

**Builder API is unchanged.** Any code using `WebhookEndpoint.builder()` compiles and runs without modification. The new field defaults to an empty set (subscribe to all events), so existing behaviour is preserved.

Only code calling `new WebhookEndpoint(id, targetUrl, secret)` directly needs to add the fourth argument or switch to the builder.

`WebhookDeliveryResult` also gained a `skipped` field. Existing call sites using the `success()` / `failure()` factory methods are unaffected.

---

## Author

Dinuka Karunarathna — [github.com/karunarathnad](https://github.com/karunarathnad)

## License

MIT