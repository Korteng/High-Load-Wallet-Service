package ru.korteng.wallet.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.korteng.wallet.dto.WalletRequest;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.exception.InsufficientFundsException;
import ru.korteng.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotCommandHandlerTest {

    private static final long CHAT = 100L;

    @Mock
    private WalletService walletService;

    @Mock
    private TelegramUserService telegramUserService;

    private BotCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BotCommandHandler(walletService, telegramUserService, new KeywordCategoryClassifier(), "");
    }

    @Test
    void expenseMessage_isRecordedWithCategoryAndIdempotencyKey() {
        UUID walletId = UUID.randomUUID();
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.of(walletId));
        when(walletService.createOperation(any(), eq("еда"), eq("кофе"), eq("tg-100-42")))
                .thenReturn(new WalletResponse(walletId, new BigDecimal("750.00")));

        String reply = handler.handle(CHAT, 42L, "кофе 250");

        ArgumentCaptor<WalletRequest> captor = ArgumentCaptor.forClass(WalletRequest.class);
        verify(walletService).createOperation(captor.capture(), eq("еда"), eq("кофе"), eq("tg-100-42"));
        assertEquals(WalletRequest.OperationType.WITHDRAW, captor.getValue().getOperationType());
        assertEquals(walletId, captor.getValue().getId());
        assertTrue(reply.contains("750.00"));
    }

    @Test
    void insufficientFunds_isReportedToUser() {
        UUID walletId = UUID.randomUUID();
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.of(walletId));
        when(walletService.createOperation(any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException("Недостаточно средств на Вашем счёте."));

        assertEquals("Недостаточно средств на Вашем счёте.", handler.handle(CHAT, 1L, "ноутбук 99999"));
    }

    @Test
    void messageBeforeStart_asksToStart() {
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.empty());

        assertEquals("Сначала отправьте /start.", handler.handle(CHAT, 1L, "кофе 250"));
        verify(walletService, never()).createOperation(any(), any(), any(), any());
    }

    @Test
    void chatOutsideAllowList_isRejectedWithoutTouchingWallets() {
        BotCommandHandler restricted = new BotCommandHandler(
                walletService, telegramUserService, new KeywordCategoryClassifier(), "1, 2");

        assertEquals("Доступ ограничен.", restricted.handle(999L, 1L, "/balance"));
        verifyNoInteractions(walletService, telegramUserService);
    }

    @Test
    void commandWithBotSuffix_isRecognized() {
        UUID walletId = UUID.randomUUID();
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.of(walletId));
        when(walletService.getWallet(walletId))
                .thenReturn(new ru.korteng.wallet.model.Wallet(walletId, new BigDecimal("12.5")));

        assertEquals("Баланс: 12.50", handler.handle(CHAT, 1L, "/balance@my_wallet_bot"));
    }
}
