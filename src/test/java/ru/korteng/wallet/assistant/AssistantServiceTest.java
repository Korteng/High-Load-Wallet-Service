package ru.korteng.wallet.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    private final WalletTools tools = mock(WalletTools.class);

    private AssistantService service(AssistantModel.Plan plan, Optional<String> phrased) {
        AssistantModel model = new AssistantModel() {
            @Override
            public Plan plan(String question, String today) {
                return plan;
            }

            @Override
            public Optional<String> phrase(String question, String dataJson) {
                return phrased;
            }
        };
        return new AssistantService(model, tools);
    }

    @Test
    void balance_usesModelPhrasing() {
        when(tools.getBalance(any(ToolContext.class))).thenReturn(new WalletTools.Balance(new BigDecimal("1500")));
        String answer = service(new AssistantModel.Plan("balance", null, null, null), Optional.of("У вас 1500 ₽")).ask(1L, "баланс?");
        assertEquals("У вас 1500 ₽", answer);
    }

    @Test
    void balance_fallsBackToTemplateWhenPhrasingEmpty() {
        when(tools.getBalance(any(ToolContext.class))).thenReturn(new WalletTools.Balance(new BigDecimal("1500")));
        String answer = service(new AssistantModel.Plan("balance", null, null, null), Optional.empty()).ask(1L, "баланс?");
        assertTrue(answer.contains("1500"));
    }

    @Test
    void spending_unknownCategoryIsDropped() {
        when(tools.getSpending(eq(7), eq(null), any(ToolContext.class)))
                .thenReturn(new WalletTools.SpendingReport(7, BigDecimal.ZERO, List.of()));
        service(new AssistantModel.Plan("spending", 7, "криптовалюта", null), Optional.empty()).ask(1L, "траты?");
        verify(tools).getSpending(eq(7), eq(null), any(ToolContext.class));
    }

    @Test
    void unknownAction_isOutOfScope_andNoToolsCalled() {
        String answer = service(new AssistantModel.Plan("transfer", null, null, null), Optional.empty()).ask(1L, "переведи");
        assertTrue(answer.contains("баланс"));
        verifyNoInteractions(tools);
    }

    @Test
    void nonePlan_isOutOfScope() {
        service(new AssistantModel.Plan("none", null, null, null), Optional.empty()).ask(1L, "анекдот");
        verify(tools, never()).getBalance(any());
    }

    @Test
    void toolFailure_returnsFailureText() {
        when(tools.getBalance(any(ToolContext.class))).thenThrow(new IllegalStateException("no wallet"));
        String answer = service(new AssistantModel.Plan("balance", null, null, null), Optional.empty()).ask(1L, "баланс?");
        assertEquals("Не получилось ответить, попробуйте позже.", answer);
    }

    @Test
    void planFailure_fallsBackToKeywordPlanner() {
        AssistantModel broken = new AssistantModel() {
            @Override
            public Plan plan(String question, String today) {
                throw new RuntimeException("empty body");
            }

            @Override
            public Optional<String> phrase(String question, String dataJson) {
                return Optional.empty();
            }
        };
        when(tools.getBalance(any(ToolContext.class))).thenReturn(new WalletTools.Balance(new BigDecimal("42")));
        String answer = new AssistantService(broken, tools).ask(1L, "Какой у меня баланс?");
        assertTrue(answer.contains("42"));
    }
}
