package io.github.karunarathnad.webhook.signature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256SignatureStrategyTest {

    private final io.github.karunarathnad.webhook.signature.HmacSha256SignatureStrategy strategy = new HmacSha256SignatureStrategy();

    @Test
    void signProducesSha256PrefixedHexString() {
        String signature = strategy.sign("{\"foo\":\"bar\"}", "mysecret");
        assertThat(signature).startsWith("sha256=").hasSize(71); // "sha256=" + 64 hex chars
    }

    @Test
    void signIsDeterministic() {
        String sig1 = strategy.sign("payload", "secret");
        String sig2 = strategy.sign("payload", "secret");
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void differentPayloadsProduceDifferentSignatures() {
        String sig1 = strategy.sign("payload1", "secret");
        String sig2 = strategy.sign("payload2", "secret");
        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void nullSecretReturnsNull() {
        assertThat(strategy.sign("payload", null)).isNull();
    }

    @Test
    void blankSecretReturnsNull() {
        assertThat(strategy.sign("payload", "  ")).isNull();
    }

    @Test
    void headerNameIsCorrect() {
        assertThat(strategy.headerName()).isEqualTo("X-Webhook-Signature");
    }
}