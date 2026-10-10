package io.github.karunarathnad.webhook.autoconfigure;

import io.github.karunarathnad.webhook.async.AsyncWebhookDispatcher;
import io.github.karunarathnad.webhook.audit.AuditLogger;
import io.github.karunarathnad.webhook.audit.Slf4jAuditLogger;
import io.github.karunarathnad.webhook.audit.WebhookAuditRecord;
import io.github.karunarathnad.webhook.core.WebhookClient;
import io.github.karunarathnad.webhook.delivery.LoggingWebhookDeliveryListener;
import io.github.karunarathnad.webhook.delivery.WebhookDeliveryListener;
import io.github.karunarathnad.webhook.http.WebhookHttpSender;
import io.github.karunarathnad.webhook.secret.DefaultWebhookSecretManager;
import io.github.karunarathnad.webhook.secret.WebhookSecretManager;
import io.github.karunarathnad.webhook.signature.HmacSha256SignatureStrategy;
import io.github.karunarathnad.webhook.signature.SignatureStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class));

    @Test
    void registersDefaultBeansForTheWholeStack() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SignatureStrategy.class);
            assertThat(context.getBean(SignatureStrategy.class)).isInstanceOf(HmacSha256SignatureStrategy.class);

            assertThat(context).hasSingleBean(AuditLogger.class);
            assertThat(context.getBean(AuditLogger.class)).isInstanceOf(Slf4jAuditLogger.class);

            assertThat(context).hasSingleBean(WebhookDeliveryListener.class);
            assertThat(context.getBean(WebhookDeliveryListener.class)).isInstanceOf(LoggingWebhookDeliveryListener.class);

            assertThat(context).hasSingleBean(WebhookSecretManager.class);
            assertThat(context.getBean(WebhookSecretManager.class)).isInstanceOf(DefaultWebhookSecretManager.class);

            assertThat(context).hasSingleBean(WebhookHttpSender.class);
            assertThat(context).hasSingleBean(AsyncWebhookDispatcher.class);
            assertThat(context).hasSingleBean(WebhookClient.class);
        });
    }

    @Test
    void backsOffWhenApplicationDefinesItsOwnAuditLogger() {
        contextRunner.withUserConfiguration(CustomAuditLoggerConfig.class).run(context -> {
            assertThat(context).hasSingleBean(AuditLogger.class);
            assertThat(context.getBean(AuditLogger.class)).isInstanceOf(NoopAuditLogger.class);
        });
    }

    @Configuration
    static class CustomAuditLoggerConfig {
        @Bean
        AuditLogger webhookAuditLogger() {
            return new NoopAuditLogger();
        }
    }

    static class NoopAuditLogger implements AuditLogger {
        @Override
        public void log(WebhookAuditRecord record) {
        }
    }
}
