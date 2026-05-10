package io.github.karunarathnad.webhook.signature;

public interface SignatureStrategy {

    // Return null when secret is null/blank — the header is then omitted from the request
    String sign(String body, String secret);

    String headerName();
}
