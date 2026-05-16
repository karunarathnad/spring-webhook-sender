package io.github.karunarathnad.webhook.signature;

/**
 * Strategy for computing and attaching a request signature to outgoing webhooks.
 *
 * <p>The library calls {@link #sign} with the serialised JSON body and the endpoint
 * secret before each HTTP request. If the returned value is non-null it is set as the
 * value of the header named by {@link #headerName}. A null return value suppresses the
 * header entirely, which is the expected behaviour when no secret is configured.
 *
 * <p>The default implementation is {@link HmacSha256SignatureStrategy}, which follows
 * the same convention used by GitHub and Stripe. Override this bean to use a different
 * algorithm or header format:
 *
 * <pre>{@code
 * @Bean
 * public SignatureStrategy webhookSignatureStrategy() {
 *     return new MyCustomSignatureStrategy();
 * }
 * }</pre>
 */
public interface SignatureStrategy {

    /**
     * Computes a signature for the given request body using the provided secret.
     *
     * <p>Return {@code null} when the secret is absent or blank to indicate that
     * no signature header should be added to the request.
     *
     * @param body   the serialised JSON request body; never {@code null}
     * @param secret the signing secret configured on the endpoint; may be {@code null}
     * @return the computed signature string, or {@code null} if signing is not applicable
     */
    String sign(String body, String secret);

    /**
     * Returns the name of the HTTP header that will carry the signature value.
     *
     * @return the header name; never {@code null}
     */
    String headerName();
}