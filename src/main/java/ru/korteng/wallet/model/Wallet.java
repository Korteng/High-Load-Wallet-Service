package ru.korteng.wallet.model;

import jakarta.persistence.*;
import ru.korteng.wallet.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    public Wallet(UUID id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    public Wallet() {
    }

    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма должна быть положительной.");
        }
        this.balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма должна быть положительной.");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Недостаточно средств на Вашем счёте.");
        }
        this.balance = balance.subtract(amount);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
