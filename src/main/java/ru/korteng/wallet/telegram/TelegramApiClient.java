package ru.korteng.wallet.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Minimal Telegram Bot API client over RestClient (long polling, no extra libraries).
 * The token is part of the request URL, so request URLs must never be logged.
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramApiClient {

    private final RestClient client;

    public TelegramApiClient(@Value("${telegram.token}") String token,
                             @Value("${telegram.poll-timeout-seconds:25}") int pollTimeoutSeconds) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("telegram.enabled=true, but TELEGRAM_TOKEN is empty");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(pollTimeoutSeconds + 15L));
        this.client = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + token)
                .requestFactory(factory)
                .build();
    }

    public JsonNode getUpdates(long offset, int timeoutSeconds) {
        JsonNode body = client.post()
                .uri("/getUpdates")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("offset", offset, "timeout", timeoutSeconds, "allowed_updates", List.of("message")))
                .retrieve()
                .body(JsonNode.class);
        if (body == null || !body.path("ok").asBoolean()) {
            throw new IllegalStateException("Telegram getUpdates returned an error response");
        }
        return body.path("result");
    }

    public void sendMessage(long chatId, String text) {
        client.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
