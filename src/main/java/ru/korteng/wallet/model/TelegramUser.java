package ru.korteng.wallet.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_user")
public class TelegramUser {

    @Id
    @Column(name = "chat_id", nullable = false, updatable = false)
    private Long chatId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TelegramUser() {
    }

    public TelegramUser(Long chatId, UUID walletId) {
        this.chatId = chatId;
        this.walletId = walletId;
        this.createdAt = Instant.now();
    }

    public Long getChatId() { return chatId; }
    public UUID getWalletId() { return walletId; }
    public Instant getCreatedAt() { return createdAt; }
}
