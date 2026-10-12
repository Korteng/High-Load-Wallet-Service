package ru.korteng.wallet.dto;

import ru.korteng.wallet.model.WalletOperation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OperationResponse(UUID id,
                                String type,
                                BigDecimal amount,
                                BigDecimal balanceAfter,
                                String category,
                                String description,
                                Instant createdAt) {

    public static OperationResponse from(WalletOperation op) {
        return new OperationResponse(op.getId(), op.getOperationType().name(), op.getAmount(),
                op.getBalanceAfter(), op.getCategory(), op.getDescription(), op.getCreatedAt());
    }
}
