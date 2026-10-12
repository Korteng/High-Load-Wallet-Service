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
    private final PerChatExecutor executor;
    private final OffsetTracker offsets = new OffsetTracker();

    private volatile boolean running;
    private Thread thread;

    public TelegramPoller(TelegramApiClient api,
                          BotCommandHandler handler,
                          @Value("${telegram.poll-timeout-seconds:25}") int pollTimeoutSeconds,
                          @Value("${telegram.worker-threads:8}") int workerThreads) {
        this.api = api;
        this.handler = handler;
        this.pollTimeoutSeconds = pollTimeoutSeconds;
        this.executor = new PerChatExecutor(Math.max(1, workerThreads));
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
        executor.shutdown();
    }

    private void loop() {
        while (running) {
            try {
                JsonNode updates = api.getUpdates(offsets.nextOffset(), pollTimeoutSeconds);
                int fresh = 0;
                for (JsonNode update : updates) {
                    if (dispatch(update)) {
                        fresh++;
                    }
                }
                // while an update is still being handled, Telegram redelivers it (and the ones after it) at
                // once; without a pause that would be a busy loop
                if (updates.size() > 0 && fresh == 0) {
                    sleep(300);
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

    /**
     * The polling thread only fetches updates; handling (and slow LLM calls) happens on the workers, per chat.
     * An update is acknowledged to Telegram only after it has been handled (see {@link OffsetTracker}).
     *
     * @return false for a redelivered update that was already dispatched
     */
    private boolean dispatch(JsonNode update) {
        long updateId = update.path("update_id").asLong();
        JsonNode message = update.path("message");
        boolean handled = !message.isMissingNode() && message.hasNonNull("text");
        if (!offsets.register(updateId, handled)) {
            return false;
        }
        if (handled) {
            long chatId = message.path("chat").path("id").asLong();
            executor.submit(chatId, () -> {
                try {
                    process(update);
                } finally {
                    offsets.done(updateId);
                }
            });
        }
        return true;
    }

    private void process(JsonNode update) {
        JsonNode message = update.path("message");
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
