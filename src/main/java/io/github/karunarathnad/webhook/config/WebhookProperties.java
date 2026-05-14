package io.github.karunarathnad.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

    private final Async async = new Async();
    private final Retry retry = new Retry();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Http http = new Http();
    private int maxPayloadSizeBytes = 262144; // 256 KB

    public Async getAsync() { return async; }
    public Retry getRetry() { return retry; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public Http getHttp() { return http; }
    public int getMaxPayloadSizeBytes() { return maxPayloadSizeBytes; }
    public void setMaxPayloadSizeBytes(int maxPayloadSizeBytes) { this.maxPayloadSizeBytes = maxPayloadSizeBytes; }

    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 16;
        private int queueCapacity = 1000;
        private Duration keepAlive = Duration.ofSeconds(60);

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public Duration getKeepAlive() { return keepAlive; }
        public void setKeepAlive(Duration keepAlive) { this.keepAlive = keepAlive; }
    }

    public static class Retry {
        // includes the first attempt, so 3 = 1 original + 2 retries
        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofSeconds(1);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofSeconds(30);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public Duration getInitialInterval() { return initialInterval; }
        public void setInitialInterval(Duration initialInterval) { this.initialInterval = initialInterval; }

        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

        public Duration getMaxInterval() { return maxInterval; }
        public void setMaxInterval(Duration maxInterval) { this.maxInterval = maxInterval; }
    }

    public static class CircuitBreaker {
        private float failureRateThreshold = 50;
        private int minimumNumberOfCalls = 10;
        private int slidingWindowSize = 20;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int permittedCallsInHalfOpenState = 3;

        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }

        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }

        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }

        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }

        public int getPermittedCallsInHalfOpenState() { return permittedCallsInHalfOpenState; }
        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) { this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState; }
    }

    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(10);

        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }
}