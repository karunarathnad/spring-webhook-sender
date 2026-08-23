package io.github.karunarathnad.webhook.autoconfigure;

import io.github.karunarathnad.webhook.async.AsyncWebhookDispatcher;
import io.github.karunarathnad.webhook.audit.AuditLogger;
import io.github.karunarathnad.webhook.audit.Slf4jAuditLogger;
import io.github.karunarathnad.webhook.config.WebhookProperties;
import io.github.karunarathnad.webhook.core.DefaultWebhookClient;
import io.github.karunarathnad.webhook.core.WebhookClient;
import io.github.karunarathnad.webhook.delivery.LoggingWebhookDeliveryListener;
import io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import io.github.karunarathnad.webhook.secret.DefaultWebhookSecretManager;
import io.github.karunarathnad.webhook.secret.WebhookSecretManager;
import io.github.karunarathnad.webhook.signature.HmacSha256SignatureStrategy;
import io.github.karunarathnad.webhook.signature.SignatureStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration that wires up the whole webhook-sending stack:
 * signing, HTTP client, retry/circuit-breaker registries, async dispatch, audit
 * logging, and the public {@link WebhookClient} bean.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean}, so applications can override any
 * single collaborator (for example a custom {@link SignatureStrategy} or
 * {@code webhookObjectMapper}) without having to redefine the rest of the stack.
 */
@AutoConfiguration
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookAutoConfiguration {

    /**
     * Provides the {@link SignatureStrategy} used to sign outgoing requests.
     *
     * <p>Defaults to {@link HmacSha256SignatureStrategy}. Define a bean of type
     * {@code SignatureStrategy} to use a different signing scheme.
     *
     * @return the default HMAC-SHA256 signature strategy
     */
    @Bean
    @ConditionalOnMissingBean
    public SignatureStrategy webhookSignatureStrategy() {
        return new HmacSha256SignatureStrategy();
    }

    /**
     * Provides the {@link AuditLogger} that records the outcome of each delivery attempt.
     *
     * <p>Defaults to {@link Slf4jAuditLogger}, which writes one structured log line per
     * attempt to the {@code webhook.audit} logger. Define a bean of type {@code AuditLogger}
     * to send audit records elsewhere, for example to a database or message queue.
     *
     * @return the default SLF4J-backed audit logger
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogger webhookAuditLogger() {
        return new Slf4jAuditLogger();
    }

    /**
     * Provides the {@link WebhookDeliveryListener} notified of delivery lifecycle events.
     *
     * <p>Defaults to {@link LoggingWebhookDeliveryListener}, which logs successes at INFO
     * and permanent failures at ERROR. Define a bean of type {@code WebhookDeliveryListener}
     * to react to deliveries in application code, for example to update order state.
     *
     * @return the default logging delivery listener
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookDeliveryListener webhookDeliveryListener() {
        return new LoggingWebhookDeliveryListener();
    }

    /**
     * Provides the {@link WebhookSecretManager} used to generate endpoint signing secrets.
     *
     * <p>Defaults to {@link DefaultWebhookSecretManager}, which generates {@code whsec_}-prefixed
     * secrets from {@link java.security.SecureRandom}. Define a bean of type
     * {@code WebhookSecretManager} to source secrets from an external vault or store instead.
     *
     * @return the default secure-random-backed secret manager
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookSecretManager webhookSecretManager() {
        return new DefaultWebhookSecretManager();
    }

    /**
     * Provides the {@link ObjectMapper} used to serialise event payloads and audit data.
     *
     * <p>Registers {@link JavaTimeModule} for {@code java.time} support and writes dates as
     * ISO-8601 strings rather than numeric timestamps. Define your own bean named
     * {@code webhookObjectMapper} to customise serialisation, for example to add a naming
     * strategy or additional modules.
     *
     * @return the default Jackson object mapper used throughout the library
     */
    @Bean
    @ConditionalOnMissingBean(name = "webhookObjectMapper")
    public ObjectMapper webhookObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Provides the {@link CloseableHttpClient} used to execute outgoing webhook requests.
     *
     * <p>Automatic retries are disabled deliberately: Resilience4j's {@code Retry} (applied in
     * {@link WebhookHttpSender}) already owns all retry decisions, including HTTP 429 and
     * {@code Retry-After} handling. Apache HttpClient5 retries 429/503 responses on its own by
     * default, which would silently double the number of HTTP round trips per logical attempt
     * and double-apply the {@code Retry-After} wait. The client is closed automatically on
     * application shutdown. Define your own bean named {@code webhookHttpClient} to customise
     * connection pooling, TLS, or proxy settings.
     *
     * @return the default Apache HttpClient5 client with automatic retries disabled
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "webhookHttpClient")
    public CloseableHttpClient webhookHttpClient() {
        // Resilience4j's Retry (see WebhookHttpSender) already owns all retry decisions,
        // including 429/Retry-After handling. Apache HttpClient5 retries 429/503
        // responses on its own by default, which would silently double the number of
        // HTTP round trips per logical attempt and double-apply the Retry-After wait.
        return HttpClients.custom()
                .disableAutomaticRetries()
                .build();
    }

    /**
     * Provides the {@link RestClient} used to send webhook HTTP requests.
     *
     * <p>Wraps {@code webhookHttpClient} with connect and read timeouts sourced from
     * {@code webhook.http.connect-timeout} and {@code webhook.http.read-timeout}. Define your
     * own bean named {@code webhookRestClient} to fully control request-factory configuration.
     *
     * @param properties        the library's configuration properties, used for HTTP timeouts
     * @param webhookHttpClient the underlying Apache HttpClient5 client
     * @return the default {@code RestClient} used to dispatch webhook requests
     */
    @Bean
    @ConditionalOnMissingBean(name = "webhookRestClient")
    public RestClient webhookRestClient(WebhookProperties properties, CloseableHttpClient webhookHttpClient) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(webhookHttpClient);
        factory.setConnectTimeout((int) properties.getHttp().getConnectTimeout().toMillis());
        factory.setConnectionRequestTimeout((int) properties.getHttp().getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getHttp().getReadTimeout().toMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Provides the {@link WebhookHttpSender} that performs a single delivery attempt,
     * orchestrating signing, the HTTP call, retry/circuit-breaker policy, and audit logging.
     *
     * @param webhookRestClient       the HTTP client used to send requests
     * @param webhookSignatureStrategy the strategy used to sign requests
     * @param webhookObjectMapper     the mapper used to serialise event payloads
     * @param webhookAuditLogger      the logger notified of each delivery attempt's outcome
     * @param webhookDeliveryListener the listener notified of delivery lifecycle events
     * @param properties              the library's configuration properties, used for retry
     *                                and circuit-breaker settings
     * @return the default HTTP sender used by {@link WebhookClient}
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookHttpSender webhookHttpSender(RestClient webhookRestClient,
                                                SignatureStrategy webhookSignatureStrategy,
                                                ObjectMapper webhookObjectMapper,
                                                AuditLogger webhookAuditLogger,
                                                WebhookDeliveryListener webhookDeliveryListener,
                                                WebhookProperties properties) {
        return new WebhookHttpSender(
                webhookRestClient,
                webhookSignatureStrategy,
                webhookObjectMapper,
                webhookAuditLogger,
                webhookDeliveryListener,
                properties);
    }

    /**
     * Provides the {@link AsyncWebhookDispatcher} that backs {@link WebhookClient}'s
     * fire-and-forget sends with a dedicated thread pool.
     *
     * <p>Pool sizing comes from {@code webhook.async.*} ({@code core-pool-size},
     * {@code max-pool-size}, {@code queue-capacity}, {@code keep-alive}). The executor waits
     * up to 30 seconds for in-flight tasks to complete on application shutdown.
     *
     * @param webhookHttpSender       the sender used to execute each dispatched delivery
     * @param webhookDeliveryListener the listener notified of delivery lifecycle events
     * @param properties              the library's configuration properties, used for thread
     *                                pool sizing
     * @return the default async dispatcher, backed by a {@link ThreadPoolTaskExecutor}
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public AsyncWebhookDispatcher asyncWebhookDispatcher(WebhookHttpSender webhookHttpSender,
                                                          WebhookDeliveryListener webhookDeliveryListener,
                                                          WebhookProperties properties) {
        WebhookProperties.Async cfg = properties.getAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setKeepAliveSeconds((int) cfg.getKeepAlive().getSeconds());
        executor.setThreadNamePrefix("webhook-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return new AsyncWebhookDispatcher(executor, webhookHttpSender, webhookDeliveryListener);
    }

    /**
     * Provides the public {@link WebhookClient} that application code injects to send events.
     *
     * @param asyncWebhookDispatcher the dispatcher backing async sends
     * @return the default client implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookClient webhookClient(AsyncWebhookDispatcher asyncWebhookDispatcher) {
        return new DefaultWebhookClient(asyncWebhookDispatcher);
    }
}