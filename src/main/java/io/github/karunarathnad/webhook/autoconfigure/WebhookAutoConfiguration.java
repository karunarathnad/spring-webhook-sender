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

@AutoConfiguration
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SignatureStrategy webhookSignatureStrategy() {
        return new HmacSha256SignatureStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogger webhookAuditLogger() {
        return new Slf4jAuditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookDeliveryListener webhookDeliveryListener() {
        return new LoggingWebhookDeliveryListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookSecretManager webhookSecretManager() {
        return new DefaultWebhookSecretManager();
    }

    @Bean
    @ConditionalOnMissingBean(name = "webhookObjectMapper")
    public ObjectMapper webhookObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

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

    @Bean
    @ConditionalOnMissingBean
    public WebhookClient webhookClient(AsyncWebhookDispatcher asyncWebhookDispatcher) {
        return new DefaultWebhookClient(asyncWebhookDispatcher);
    }
}