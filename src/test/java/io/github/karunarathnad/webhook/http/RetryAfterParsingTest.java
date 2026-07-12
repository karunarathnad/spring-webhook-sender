package io.github.karunarathnad.webhook.http;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterParsingTest {

    @Test
    void nullHeaderReturnsZero() {
        assertThat(WebhookHttpSender.parseRetryAfter(null)).isZero();
    }

    @Test
    void blankHeaderReturnsZero() {
        assertThat(WebhookHttpSender.parseRetryAfter("   ")).isZero();
    }

    @Test
    void numericSecondsAreConvertedToMillis() {
        assertThat(WebhookHttpSender.parseRetryAfter("5")).isEqualTo(5000L);
    }

    @Test
    void numericHeaderWithSurroundingWhitespaceIsTrimmed() {
        assertThat(WebhookHttpSender.parseRetryAfter("  12  ")).isEqualTo(12000L);
    }

    @Test
    void malformedHeaderReturnsZero() {
        assertThat(WebhookHttpSender.parseRetryAfter("not-a-valid-value")).isZero();
    }

    @Test
    void httpDateInTheFutureIsConvertedToRemainingMillis() {
        Instant retryAt = Instant.now().plusSeconds(10).truncatedTo(ChronoUnit.SECONDS);
        String header = DateTimeFormatter.RFC_1123_DATE_TIME.format(retryAt.atZone(ZoneOffset.UTC));

        long millis = WebhookHttpSender.parseRetryAfter(header);

        assertThat(millis).isBetween(8000L, 10000L);
    }

    @Test
    void httpDateInThePastReturnsZero() {
        Instant retryAt = Instant.now().minusSeconds(60);
        String header = DateTimeFormatter.RFC_1123_DATE_TIME.format(retryAt.atZone(ZoneOffset.UTC));

        assertThat(WebhookHttpSender.parseRetryAfter(header)).isZero();
    }
}
