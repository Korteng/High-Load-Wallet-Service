package ru.korteng.wallet.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.model.ApiKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * GigaChat access tokens live 30 minutes and are obtained with an authorization key via OAuth.
 * Used as the "API key" of the OpenAI-compatible client: the token is refreshed shortly before it expires.
 * Neither the authorization key nor the token is ever logged.
 */
@Component
@ConditionalOnProperty(name = "llm.enabled", havingValue = "true")
public class GigaChatTokenProvider implements ApiKey {

    private static final long REFRESH_MARGIN_MS = 60_000;

    private final RestClient restClient;
    private final String authKey;
    private final String scope;
    private final String oauthUrl;

    private String token;
    private long expiresAtMs;

    public GigaChatTokenProvider(@Value("${gigachat.auth-key:}") String authKey,
                                 @Value("${gigachat.scope:GIGACHAT_API_PERS}") String scope,
                                 @Value("${gigachat.oauth-url}") String oauthUrl) {
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("llm.enabled=true but GIGACHAT_AUTH_KEY is empty");
        }
        this.authKey = authKey.trim();
        this.scope = scope;
        this.oauthUrl = oauthUrl;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public synchronized String getValue() {
        if (token == null || System.currentTimeMillis() >= expiresAtMs - REFRESH_MARGIN_MS) {
            refresh();
        }
        return token;
    }

    private void refresh() {
        JsonNode response = restClient.post()
                .uri(oauthUrl)
                .header("Authorization", "Basic " + authKey)
                .header("RqUID", UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("scope=" + scope)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("GigaChat token response has no access_token");
        }
        token = response.get("access_token").asText();
        expiresAtMs = response.path("expires_at").asLong(System.currentTimeMillis() + 25 * 60_000);
    }
}
