package ru.korteng.wallet.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * OpenAI-compatible client pointed at GigaChat. Built by hand (no Spring AI starter) so that nothing is
 * auto-configured, and the application starts without any LLM settings when llm.enabled=false.
 */
@Configuration
@ConditionalOnProperty(name = "llm.enabled", havingValue = "true")
public class LlmConfig {

    @Bean
    public ChatClient llmChatClient(GigaChatTokenProvider tokens,
                                    @Value("${llm.base-url}") String baseUrl,
                                    @Value("${llm.model}") String model) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(tokens)
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.0)
                        .maxTokens(400)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }
}
