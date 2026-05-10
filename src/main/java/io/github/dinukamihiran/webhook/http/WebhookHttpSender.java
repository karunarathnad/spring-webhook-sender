package io.github.karunarathnad.webhook.http;

import io.github.karunarathnad.webhook.audit.AuditLogger;
import io.github.karunarathnad.webhook.audit.WebhookAuditRecord;
import io.github.karunarathnad.webhook.config.WebhookProperties;
import io.github.karunarathnad.webhook.core.WebhookDeliveryResult;
import io.github.karunarathnad.webhook.core.WebhookEndpoint;
import io.github.karunarathnad.webhook.core.WebhookEvent;
import io.github.karunarathnad.webhook.exception.WebhookDeliveryException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class WebhookHttpSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);

    private final RestClient restClient;
    private final SignatureStrategy signatureStrategy;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public WebhookHttpSender(RestClient restClient,
                              SignatureStrategy signatureStrategy,
                              ObjectMapper objectMapper,
                              AuditLogger auditLogger,
                              WebhookProperties properties) {
        this.restClient = restClient;
        this.signatureStrategy = signatureStrategy;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
        this.circuitBreakerRegistry = buildCircuitBreakerRegistry(properties.getCircuitBreaker());
        this.retryRegistry = buildRetryRegistry(properties.getRetry());
    }

    public WebhookDeliveryResult send(WebhookEvent event, WebhookEndpoint endpoint) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(endpoint.id());
        Retry retry = retryRegistry.retry(endpoint.id());

        AtomicInteger attemptCounter = new AtomicInteger(0);
        Instant start = Instant.now();

        retry.getEventPublisher().onRetry(e ->
                log.debug("Retrying webhook eventId={} endpointId={} attempt={}",
                        event.eventId(), endpoint.id(), e.getNumberOfRetryAttempts() + 1));

        Callable<WebhookDeliveryResult> decorated =
                CircuitBreaker.decorateCallable(cb,
                        Retry.decorateCallable(retry,
                                () -> doSend(event, endpoint, attemptCounter.incrementAndGet())));
        try {
            return decorated.call();
        } catch (WebhookDeliveryException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            return WebhookDeliveryResult.failure(
                    event.eventId(), endpoint.id(),
                    e.getHttpStatus(), e.getMessage(),
                    attemptCounter.get(), elapsed);
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            return WebhookDeliveryResult.failure(
                    event.eventId(), endpoint.id(),
                    -1, e.getMessage(),
                    attemptCounter.get(), elapsed);
        }
    }

    private WebhookDeliveryResult doSend(WebhookEvent event, WebhookEndpoint endpoint, int attempt) {
        Instant start = Instant.now();
        String body;
        try {
            body = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new WebhookDeliveryException.NonRetryable("Payload serialisation failed", -1);
        }

        String signature = signatureStrategy.sign(body, endpoint.secret());

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(endpoint.targetUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(body);

            if (signature != null) {
                request = request.header(signatureStrategy.headerName(), signature);
            }

            Integer statusCode = request.retrieve()
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
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(cfg.getMaxAttempts())
                .intervalFunction(attempt -> {
                    long ms = (long) (cfg.getInitialInterval().toMillis()
                            * Math.pow(cfg.getMultiplier(), attempt - 1));
                    return Math.min(ms, cfg.getMaxInterval().toMillis());
                })
                .retryOnException(e -> !(e instanceof WebhookDeliveryException.NonRetryable))
                .build();
        return RetryRegistry.of(config);
    }
}