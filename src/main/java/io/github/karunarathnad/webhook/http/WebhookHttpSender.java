package io.github.karunarathnad.webhook.http;

import io.github.karunarathnad.webhook.audit.AuditLogger;
import io.github.karunarathnad.webhook.audit.WebhookAuditRecord;
import io.github.karunarathnad.webhook.config.WebhookProperties;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener;
import io.github.karunarathnad.webhook.exception.WebhookDeliveryException;
import io.github.karunarathnad.webhook.exception.WebhookPayloadTooLargeException;
import io.github.karunarathnad.webhook.exception.WebhookRateLimitedException;
import io.github.karunarathnad.webhook.signature.SignatureStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class WebhookHttpSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);

    private final RestClient restClient;
    private final SignatureStrategy signatureStrategy;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final WebhookDeliveryListener deliveryListener;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final int maxPayloadSizeBytes;

    public WebhookHttpSender(RestClient restClient,
                              SignatureStrategy signatureStrategy,
                              ObjectMapper objectMapper,
                              AuditLogger auditLogger,
                              WebhookDeliveryListener deliveryListener,
                              WebhookProperties properties) {
        this.restClient = restClient;
        this.signatureStrategy = signatureStrategy;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
        this.deliveryListener = deliveryListener;
        this.maxPayloadSizeBytes = properties.getMaxPayloadSizeBytes();
        this.circuitBreakerRegistry = buildCircuitBreakerRegistry(properties.getCircuitBreaker());
        this.retryRegistry = buildRetryRegistry(properties.getRetry());
    }

    public WebhookDeliveryResult send(WebhookEvent event, WebhookEndpoint endpoint) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(endpoint.id());
        Retry retry = retryRegistry.retry(endpoint.id());

        AtomicInteger attemptCounter = new AtomicInteger(0);
        Instant start = Instant.now();

        Callable<WebhookDeliveryResult> decorated =
                CircuitBreaker.decorateCallable(cb,
                        Retry.decorateCallable(retry,
                                () -> doSend(event, endpoint, attemptCounter.incrementAndGet())));
        try {
            WebhookDeliveryResult result = decorated.call();
            deliveryListener.onSuccess(event, endpoint, result);
            return result;
        } catch (WebhookDeliveryException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            WebhookDeliveryResult result = WebhookDeliveryResult.failure(
                    event.eventId(), endpoint.id(),
                    e.getHttpStatus(), e.getMessage(),
                    attemptCounter.get(), elapsed);
            deliveryListener.onPermanentFailure(event, endpoint, result);
            return result;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            WebhookDeliveryResult result = WebhookDeliveryResult.failure(
                    event.eventId(), endpoint.id(),
                    -1, e.getMessage(),
                    attemptCounter.get(), elapsed);
            deliveryListener.onPermanentFailure(event, endpoint, result);
            return result;
        }
    }

    private WebhookDeliveryResult doSend(WebhookEvent event, WebhookEndpoint endpoint, int attempt) {
        Instant start = Instant.now();
        if (attempt > 1) {
            log.debug("Retrying webhook eventId={} endpointId={} attempt={}",
                    event.eventId(), endpoint.id(), attempt);
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new WebhookDeliveryException.NonRetryable("Payload serialisation failed", -1);
        }

        int sizeBytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (sizeBytes > maxPayloadSizeBytes) {
            throw new WebhookPayloadTooLargeException(sizeBytes, maxPayloadSizeBytes);
        }

        String signature = signatureStrategy.sign(body, endpoint.secret());

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(endpoint.targetUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(body);

            for (Map.Entry<String, String> entry : endpoint.headers().entrySet()) {
                request = request.header(entry.getKey(), entry.getValue());
            }

            if (signature != null) {
                request = request.header(signatureStrategy.headerName(), signature);
            }

            Integer statusCode = request.retrieve()
                    .onStatus(status -> status.value() == 429, (req, resp) -> {
                        String retryAfterHeader = resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                        long retryAfterMs = parseRetryAfter(retryAfterHeader);
                        log.warn("Webhook rate limited endpointId={} retryAfterMs={}", endpoint.id(), retryAfterMs);
                        throw new WebhookRateLimitedException(retryAfterMs);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        throw new WebhookDeliveryException.NonRetryable(
                                "Client error " + resp.getStatusCode().value(), resp.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new WebhookDeliveryException(
                                "Server error " + resp.getStatusCode().value(), resp.getStatusCode().value());
                    })
                    .toBodilessEntity()
                    .getStatusCode()
                    .value();

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            auditLogger.log(WebhookAuditRecord.of(event, endpoint, statusCode, true, null, attempt, durationMs));

            return WebhookDeliveryResult.success(
                    event.eventId(), endpoint.id(), statusCode, attempt, Duration.ofMillis(durationMs));

        } catch (WebhookDeliveryException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            auditLogger.log(WebhookAuditRecord.of(event, endpoint, e.getHttpStatus(), false, e.getMessage(), attempt, durationMs));
            throw e;
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            auditLogger.log(WebhookAuditRecord.of(event, endpoint, -1, false, e.getMessage(), attempt, durationMs));
            throw new WebhookDeliveryException("HTTP request failed", e);
        }
    }

    static long parseRetryAfter(String header) {
        if (header == null || header.isBlank()) return 0;
        try {
            return Long.parseLong(header.trim()) * 1000L;
        } catch (NumberFormatException e) {
            try {
                Instant retryAt = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(header.trim()));
                return Math.max(0, Duration.between(Instant.now(), retryAt).toMillis());
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    private static CircuitBreakerRegistry buildCircuitBreakerRegistry(WebhookProperties.CircuitBreaker cfg) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cfg.getFailureRateThreshold())
                .minimumNumberOfCalls(cfg.getMinimumNumberOfCalls())
                .slidingWindowSize(cfg.getSlidingWindowSize())
                .waitDurationInOpenState(cfg.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cfg.getPermittedCallsInHalfOpenState())
                .recordException(e -> !(e instanceof WebhookDeliveryException.NonRetryable))
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    private static RetryRegistry buildRetryRegistry(WebhookProperties.Retry cfg) {
        RetryConfig config = RetryConfig.<WebhookDeliveryResult>custom()
                .maxAttempts(cfg.getMaxAttempts())
                .intervalBiFunction((attempts, either) -> {
                    if (either.isLeft() && either.getLeft() instanceof WebhookRateLimitedException rle
                            && rle.getRetryAfterMs() > 0) {
                        return Math.min(rle.getRetryAfterMs(), cfg.getMaxInterval().toMillis());
                    }
                    long ms = (long) (cfg.getInitialInterval().toMillis()
                            * Math.pow(cfg.getMultiplier(), attempts - 1));
                    return Math.min(ms, cfg.getMaxInterval().toMillis());
                })
                .retryOnException(e -> !(e instanceof WebhookDeliveryException.NonRetryable))
                .build();
        return RetryRegistry.of(config);
    }
}