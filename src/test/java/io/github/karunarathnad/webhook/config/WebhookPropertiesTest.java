package io.github.karunarathnad.webhook.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "webhook.max-payload-size-bytes=512000",
        "webhook.http.connect-timeout=3s",
        "webhook.http.read-timeout=15s",
        "webhook.retry.max-attempts=5",
        "webhook.retry.initial-interval=2s",
        "webhook.retry.multiplier=3.0",
        "webhook.retry.max-interval=60s",
        "webhook.circuit-breaker.failure-rate-threshold=75",
        "webhook.circuit-breaker.minimum-number-of-calls=5",
        "webhook.circuit-breaker.sliding-window-size=10",
        "webhook.circuit-breaker.wait-duration-in-open-state=60s",
        "webhook.circuit-breaker.permitted-calls-in-half-open-state=1",
        "webhook.async.core-pool-size=8",
        "webhook.async.max-pool-size=32",
        "webhook.async.queue-capacity=500",
        "webhook.async.keep-alive=120s"
})
class WebhookPropertiesTest {

    @Autowired
    private WebhookProperties properties;

    @Test
    void loadsMaxPayloadSizeBytes() {
        assertThat(properties.getMaxPayloadSizeBytes()).isEqualTo(512000);
    }

    @Test
    void loadsHttpConfiguration() {
        assertThat(properties.getHttp().getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getHttp().getReadTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void loadsRetryConfiguration() {
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getRetry().getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getRetry().getMultiplier()).isEqualTo(3.0);
        assertThat(properties.getRetry().getMaxInterval()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void loadsCircuitBreakerConfiguration() {
        assertThat(properties.getCircuitBreaker().getFailureRateThreshold()).isEqualTo(75);
        assertThat(properties.getCircuitBreaker().getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(properties.getCircuitBreaker().getSlidingWindowSize()).isEqualTo(10);
        assertThat(properties.getCircuitBreaker().getWaitDurationInOpenState())
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getCircuitBreaker().getPermittedCallsInHalfOpenState()).isEqualTo(1);
    }

    @Test
    void loadsAsyncConfiguration() {
        assertThat(properties.getAsync().getCorePoolSize()).isEqualTo(8);
        assertThat(properties.getAsync().getMaxPoolSize()).isEqualTo(32);
        assertThat(properties.getAsync().getQueueCapacity()).isEqualTo(500);
        assertThat(properties.getAsync().getKeepAlive()).isEqualTo(Duration.ofSeconds(120));
    }
}

@SpringBootTest
class WebhookPropertiesDefaultsTest {
    @Autowired
    private WebhookProperties properties;

    @Test
    void defaultMaxPayloadSizeIs256KB() {
        assertThat(properties.getMaxPayloadSizeBytes()).isEqualTo(262144);
    }

    @Test
    void defaultHttpTimeouts() {
        assertThat(properties.getHttp().getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getHttp().getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void defaultRetryConfiguration() {
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getInitialInterval().toMillis()).isGreaterThan(0);
        assertThat(properties.getRetry().getMultiplier()).isGreaterThan(1.0);
        assertThat(properties.getRetry().getMaxInterval().toMillis()).isGreaterThan(0);
    }

    @Test
    void defaultCircuitBreakerConfiguration() {
        assertThat(properties.getCircuitBreaker().getFailureRateThreshold()).isEqualTo(50);
        assertThat(properties.getCircuitBreaker().getMinimumNumberOfCalls()).isGreaterThan(0);
        assertThat(properties.getCircuitBreaker().getSlidingWindowSize()).isGreaterThan(0);
        assertThat(properties.getCircuitBreaker().getWaitDurationInOpenState())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getCircuitBreaker().getPermittedCallsInHalfOpenState()).isGreaterThan(0);
    }

    @Test
    void defaultAsyncConfiguration() {
        assertThat(properties.getAsync().getCorePoolSize()).isGreaterThan(0);
        assertThat(properties.getAsync().getMaxPoolSize()).isGreaterThan(0);
        assertThat(properties.getAsync().getQueueCapacity()).isGreaterThan(0);
        assertThat(properties.getAsync().getKeepAlive()).isEqualTo(Duration.ofSeconds(60));
    }
}

