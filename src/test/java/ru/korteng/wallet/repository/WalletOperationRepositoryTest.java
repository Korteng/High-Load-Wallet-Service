package ru.korteng.wallet.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.korteng.wallet.model.Wallet;
import ru.korteng.wallet.model.WalletOperation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.korteng.wallet.dto.WalletRequest.OperationType.DEPOSIT;
import static ru.korteng.wallet.dto.WalletRequest.OperationType.WITHDRAW;

/**
 * Runs the real Liquibase changelog against a real PostgreSQL container (Hibernate does not create the schema),
 * so the migrations themselves are verified.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WalletOperationRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private WalletRepository wallets;

    @Autowired
    private WalletOperationRepository operations;

    @Test
    void history_isReturnedNewestFirst() throws Exception {
        Wallet wallet = wallets.save(new Wallet(UUID.randomUUID(), new BigDecimal("1000.00")));
        operations.saveAndFlush(WalletOperation.of(wallet.getId(), WITHDRAW, new BigDecimal("100.00"),
                new BigDecimal("900.00"), "еда", "кофе", "k1"));
        Thread.sleep(10);
        operations.saveAndFlush(WalletOperation.of(wallet.getId(), DEPOSIT, new BigDecimal("500.00"),
                new BigDecimal("1400.00"), "доход", "зарплата", "k2"));

        List<WalletOperation> history =
                operations.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), PageRequest.of(0, 10));

        assertEquals(2, history.size());
        assertEquals("k2", history.get(0).getIdempotencyKey());
        assertEquals("k1", history.get(1).getIdempotencyKey());
    }

    @Test
    void idempotencyKey_isUniqueInDatabase() {
        Wallet wallet = wallets.save(new Wallet(UUID.randomUUID(), new BigDecimal("1000.00")));
        operations.saveAndFlush(WalletOperation.of(wallet.getId(), WITHDRAW, new BigDecimal("100.00"),
                new BigDecimal("900.00"), null, null, "same-key"));

        assertThrows(DataIntegrityViolationException.class, () ->
                operations.saveAndFlush(WalletOperation.of(wallet.getId(), WITHDRAW, new BigDecimal("100.00"),
                        new BigDecimal("800.00"), null, null, "same-key")));
    }

    @Test
    void findByIdempotencyKey_returnsStoredOperation() {
        Wallet wallet = wallets.save(new Wallet(UUID.randomUUID(), new BigDecimal("1000.00")));
        operations.saveAndFlush(WalletOperation.of(wallet.getId(), WITHDRAW, new BigDecimal("100.00"),
                new BigDecimal("900.00"), "еда", "кофе", "find-me"));

        assertEquals(new BigDecimal("900.00"),
                operations.findByIdempotencyKey("find-me").orElseThrow().getBalanceAfter().setScale(2));
    }
}
