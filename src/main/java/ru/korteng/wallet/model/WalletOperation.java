package ru.korteng.wallet.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import ru.korteng.wallet.dto.WalletRequest.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_operation")
public class WalletOperation implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false, length = 16)
    private OperationType operationType;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "category", length = 64, updatable = false)
    private String category;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    protected WalletOperation() {
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public static WalletOperation of(UUID walletId, OperationType type, BigDecimal amount, BigDecimal balanceAfter,
                                     String category, String description, String idempotencyKey) {
        WalletOperation op = new WalletOperation();
        op.id = UUID.randomUUID();
        op.walletId = walletId;
        op.operationType = type;
        op.amount = amount;
        op.balanceAfter = balanceAfter;
        op.category = category;
        op.description = description;
        op.idempotencyKey = idempotencyKey;
        op.createdAt = Instant.now();
        return op;
    }

    @Override
    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public OperationType getOperationType() { return operationType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
