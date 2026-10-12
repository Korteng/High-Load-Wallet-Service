package ru.korteng.wallet.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import ru.korteng.wallet.model.Wallet;
import ru.korteng.wallet.model.WalletOperation;
import ru.korteng.wallet.repository.WalletOperationRepository;
import ru.korteng.wallet.repository.WalletOperationRepository.CategoryTotal;
import ru.korteng.wallet.service.WalletService;
import ru.korteng.wallet.telegram.TelegramUserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.korteng.wallet.dto.WalletRequest.OperationType.WITHDRAW;

@ExtendWith(MockitoExtension.class)
class WalletToolsTest {

    private static final long CHAT = 100L;
    private static final UUID WALLET = UUID.randomUUID();

    @Mock
    private TelegramUserService users;

    @Mock
    private WalletService wallets;

    @Mock
    private WalletOperationRepository operations;

    private WalletTools tools;

    private final ToolContext context = new ToolContext(Map.of(WalletTools.CHAT_ID_KEY, CHAT));

    @BeforeEach
    void setUp() {
        tools = new WalletTools(users, wallets, operations);
    }

    private static CategoryTotal row(String category, String total) {
        return new CategoryTotal() {
            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    @Test
    void balance_isTakenFromTheWalletOfTheChat() {
        when(users.findWalletId(CHAT)).thenReturn(Optional.of(WALLET));
        when(wallets.getWallet(WALLET)).thenReturn(new Wallet(WALLET, new BigDecimal("5599.00")));

        assertEquals(new BigDecimal("5599.00"), tools.getBalance(context).rub());
    }

    @Test
    void spending_isSummedAndCanBeFilteredByCategory() {
        when(users.findWalletId(CHAT)).thenReturn(Optional.of(WALLET));
        when(operations.sumByCategory(eq(WALLET), eq(WITHDRAW), any(Instant.class)))
                .thenReturn(List.of(row("еда", "900"), row("транспорт", "400"), row(null, "50")));

        WalletTools.SpendingReport all = tools.getSpending(7, null, context);
        assertEquals(0, new BigDecimal("1350").compareTo(all.totalRub()));
        assertEquals("без категории", all.byCategory().get(2).category());

        WalletTools.SpendingReport food = tools.getSpending(7, " Еда ", context);
        assertEquals(1, food.byCategory().size());
        assertEquals(0, new BigDecimal("900").compareTo(food.totalRub()));
    }

    @Test
    void spendingPeriod_isClamped() {
        when(users.findWalletId(CHAT)).thenReturn(Optional.of(WALLET));
        when(operations.sumByCategory(eq(WALLET), eq(WITHDRAW), any(Instant.class))).thenReturn(List.of());

        assertEquals(365, tools.getSpending(100000, null, context).days());
        assertEquals(1, tools.getSpending(-5, null, context).days());
    }

    @Test
    void recentOperations_areLimitedAndSanitized() {
        when(users.findWalletId(CHAT)).thenReturn(Optional.of(WALLET));
        WalletOperation op = WalletOperation.of(WALLET, WITHDRAW, new BigDecimal("500.00"),
                new BigDecimal("100.00"), "прочее", "перевод 89181393070 Ивану", "k1");
        when(wallets.getHistory(WALLET, 20)).thenReturn(List.of(op));

        List<WalletTools.OperationView> result = tools.getRecentOperations(1000, context);

        verify(wallets).getHistory(WALLET, 20);
        assertEquals("перевод [номер] Ивану", result.get(0).description());
        assertTrue(result.get(0).date().length() > 10);
    }

    @Test
    void missingChatInContext_isRejected() {
        assertThrows(IllegalStateException.class, () -> tools.getBalance(new ToolContext(Map.of())));
    }

    @Test
    void chatWithoutWallet_isRejected() {
        when(users.findWalletId(CHAT)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> tools.getBalance(context));
    }
}
