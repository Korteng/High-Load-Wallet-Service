package ru.korteng.wallet.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotCommandHandlerAssistantTest {

    private static final long CHAT = 100L;

    @Mock
    private WalletService walletService;

    @Mock
    private TelegramUserService telegramUserService;

    private BotCommandHandler handlerWith(Assistant assistant) {
        return new BotCommandHandler(walletService, telegramUserService, new KeywordCategoryClassifier(), "",
                Optional.ofNullable(assistant));
    }

    @Test
    void question_goesToAssistantWithChatId() {
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.of(UUID.randomUUID()));
        AtomicReference<String> seen = new AtomicReference<>();
        BotCommandHandler handler = handlerWith((chatId, question) -> {
            seen.set(chatId + ":" + question);
            return "ответ";
        });

        assertEquals("ответ", handler.handle(CHAT, 1L, "сколько потратил на еду за неделю?"));
        assertEquals("100:сколько потратил на еду за неделю?", seen.get());
    }

    @Test
    void questionWithNumberAtTheEnd_isNotRecordedAsExpense() {
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.of(UUID.randomUUID()));
        BotCommandHandler handler = handlerWith((chatId, question) -> "ответ");

        assertEquals("ответ", handler.handle(CHAT, 1L, "покажи последние 5"));
    }

    @Test
    void expenseWhoseWordStartsLikeQuestionWord_isStillAnExpense() {
        UUID walletId = UUID.randomUUID();
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.of(walletId));
        when(walletService.createOperation(any(), any(), eq("чтение"), eq("tg-100-7")))
                .thenReturn(new WalletResponse(walletId, new BigDecimal("700.00")));
        BotCommandHandler handler = handlerWith((chatId, question) -> {
            throw new AssertionError("assistant must not be called");
        });

        assertTrue(handler.handle(CHAT, 7L, "чтение 300").startsWith("Записал: -300.00"));
        verify(walletService).createOperation(any(), any(), eq("чтение"), eq("tg-100-7"));
    }

    @Test
    void questionBeforeStart_asksToStart() {
        when(telegramUserService.findWalletId(CHAT)).thenReturn(Optional.empty());
        BotCommandHandler handler = handlerWith((chatId, question) -> "не должен вызываться");

        assertEquals("Сначала отправьте /start.", handler.handle(CHAT, 1L, "какой у меня баланс?"));
    }

    @Test
    void withoutAssistant_questionIsNotUnderstood() {
        BotCommandHandler handler = handlerWith(null);

        assertTrue(handler.handle(CHAT, 1L, "сколько потратил на еду?").startsWith("Не понял"));
    }

    @Test
    void looksLikeQuestion_recognizesQuestionsOnly() {
        assertTrue(BotCommandHandler.looksLikeQuestion("Сколько я потратил за неделю"));
        assertTrue(BotCommandHandler.looksLikeQuestion("хватит ли мне до зарплаты"));
        assertTrue(BotCommandHandler.looksLikeQuestion("баланс?"));
        assertFalse(BotCommandHandler.looksLikeQuestion("кофе 250"));
        assertFalse(BotCommandHandler.looksLikeQuestion("чтение 300"));
    }
}
