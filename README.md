# spring-webhook-sender

[![Maven Central](https://img.shields.io/maven-central/v/io.github.karunarathnad/spring-webhook-sender)](https://central.sonatype.com/artifact/io.github.karunarathnad/spring-webhook-sender)

A lightweight Spring Boot 3.x library that makes it easy to send outgoing webhooks from your application. Drop it in as a dependency, inject `WebhookClient`, and fire events. The library takes care of the rest.

Under the hood it handles HMAC-SHA256 request signing, automatic retry with exponential backoff, per-endpoint circuit breaking, non-blocking async dispatch, and audit logging. Everything is wired up automatically via Spring Boot auto-configuration and tunable through `application.yml` with sensible defaults.

## Requirements

- Java 17+
- Spring Boot 3.2+

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>spring-webhook-sender</artifactId>
    <version>1.0.0</version>
</dependency>
```

No extra configuration is required. Spring Boot picks up the auto-configuration automatically.

## Usage

Inject `WebhookClient` and call it:

```java
@Service
public class OrderService {

    @Autowired
    private WebhookClient webhookClient;

    private static final WebhookEndpoint PAYMENT_ENDPOINT = WebhookEndpoint.builder()
            .id("payment-service")
            .targetUrl("https://payments.example.com/webhooks")
            .secret(System.getenv("PAYMENT_WEBHOOK_SECRET"))
            .build();

    public void onOrderCreated(Order order) {
        WebhookEvent event = WebhookEvent.builder()
                .eventType("order.created")
                .payload(order)
                .metadata("source", "order-service")  // optional
                .build();

        // async — your thread is not blocked
        webhookClient.sendAsync(event, PAYMENT_ENDPOINT)
                .thenAccept(result -> log.info("delivered={} attempts={}", result.success(), result.totalAttempts()));

        // or blocking, e.g. in a batch job
        // WebhookDeliveryResult result = webhookClient.send(event, PAYMENT_ENDPOINT);
    }
}
```

The JSON body sent to the endpoint looks like this:

```json
{
  "eventId": "a3f1c2d4-...",
  "eventType": "order.created",
  "occurredAt": "2026-05-10T08:30:00Z",
  "metadata": { "source": "order-service" },
  "payload": { "orderId": "ORD-001", "amount": 99.99 }
}
```

The `X-Webhook-Signature` header is set to `sha256=<hmac>` using the endpoint secret. If no secret is set, the header is omitted.

`WebhookEndpoint.id` is the circuit-breaker key, so use the same value whenever you reference the same destination. A broken endpoint will only trip its own circuit. Other endpoints are not affected.

## Configuration

All settings are optional. The library ships with sensible defaults baked in, so no configuration is required to get started.

If you want to override any value, add a `webhook` block to your own application's `application.yml`:

```yaml
webhook:
  http:
    connect-timeout: 5s      # default 5s
    read-timeout: 10s        # default 10s

  retry:
    max-attempts: 3          # total attempts including the first; set to 1 to disable. default 3
    initial-interval: 1s     # wait before the 2nd attempt. default 1s
    multiplier: 2.0          # wait doubles each retry (1s to 2s to 4s ...). default 2.0
    max-interval: 30s        # upper cap on wait between retries. default 30s

  circuit-breaker:
    failure-rate-threshold: 50            # open circuit when this % of calls fail. default 50
    minimum-number-of-calls: 10           # don't evaluate rate until this many calls. default 10
    sliding-window-size: 20               # default 20
    wait-duration-in-open-state: 30s      # default 30s
    permitted-calls-in-half-open-state: 3 # default 3

  async:
    core-pool-size: 4        # default 4
    max-pool-size: 16        # default 16
    queue-capacity: 1000     # events waiting for a thread; new events rejected when full. default 1000
    keep-alive: 60s          # default 60s
```

5xx responses and network errors are retried. 4xx responses are not retried and do not count toward the circuit breaker failure rate.

## Extending

### Persist audit records to a database

By default each delivery attempt is logged via SLF4J to the `webhook.audit` logger. To save records to a database instead, register your own `AuditLogger` bean and the SLF4J default will be skipped:

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

If you need different serialisation settings (e.g. snake_case), override:

```java
@Bean
public ObjectMapper webhookObjectMapper() {
    return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
}
```

## Verifying the signature on the receiving side

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(UTF_8)));
String received = request.getHeader("X-Webhook-Signature");

// constant-time comparison to prevent timing attacks
boolean valid = MessageDigest.isEqual(expected.getBytes(UTF_8), received.getBytes(UTF_8));
```

## Author

Dinuka Karunarathna at [github.com/karunarathnad](https://github.com/karunarathnad)

## License

MIT