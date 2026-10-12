package ru.korteng.wallet.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeywordPlannerTest {

    @Test
    void spendingOnFoodForWeek() {
        var plan = KeywordPlanner.plan("Сколько я потратил на еду за неделю?");
        assertEquals("spending", plan.action());
        assertEquals(7, plan.days());
        assertEquals("еда", plan.category());
    }

    @Test
    void spendingWithoutPeriodDefaultsToMonth() {
        var plan = KeywordPlanner.plan("мои расходы");
        assertEquals(30, plan.days());
        assertNull(plan.category());
    }

    @Test
    void shortStemDoesNotMatchInsideWord() {
        assertNull(KeywordPlanner.plan("сколько потратил на победу").category());
    }

    @Test
    void historyWithNumber() {
        var plan = KeywordPlanner.plan("Покажи последние 3 операции");
        assertEquals("history", plan.action());
        assertEquals(3, plan.limit());
    }

    @Test
    void balance() {
        assertEquals("balance", KeywordPlanner.plan("Какой у меня баланс?").action());
    }

    @Test
    void unrelatedIsNone() {
        assertEquals("none", KeywordPlanner.plan("расскажи анекдот").action());
    }
}
