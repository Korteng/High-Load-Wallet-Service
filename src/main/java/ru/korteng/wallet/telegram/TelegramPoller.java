package ru.korteng.wallet.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);

    private final TelegramApiClient api;
    private final BotCommandHandler handler;
    private final int pollTimeoutSeconds;

    private volatile boolean running;
    private Thread thread;

    public TelegramPoller(TelegramApiClient api,
                          BotCommandHandler handler,
                          @Value("${telegram.poll-timeout-seconds:25}") int pollTimeoutSeconds) {
        this.api = api;
        this.handler = handler;
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    @PostConstruct
    void start() {
        running = true;
        thread = new Thread(this::loop, "telegram-poller");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram long polling started");
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        long offset = 0;
        while (running) {
            try {
                JsonNode updates = api.getUpdates(offset, pollTimeoutSeconds);
                for (JsonNode update : updates) {
                    offset = Math.max(offset, update.path("update_id").asLong() + 1);
                    process(update);
                }
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                log.warn("Telegram polling error: {}", e.getClass().getSimpleName());
                sleep(3000);
            }
        }
    }

    private void process(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode() || !message.hasNonNull("text")) {
            return;
        }
        long chatId = message.path("chat").path("id").asLong();
        long updateId = update.path("update_id").asLong();
        try {
            String reply = handler.handle(chatId, updateId, message.path("text").asText());
            if (reply != null) {
                api.sendMessage(chatId, reply);
            }
        } catch (Exception e) {
            log.error("Failed to handle update {}", updateId, e);
            try {
                api.sendMessage(chatId, "Внутренняя ошибка. Попробуйте позже.");
            } catch (Exception ignored) {
                // nothing else to do
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
