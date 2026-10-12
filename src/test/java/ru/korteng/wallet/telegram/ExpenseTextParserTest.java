package ru.korteng.wallet.telegram;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseTextParserTest {

    @Test
    void descriptionThenAmount_isExpense() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("кофе 250").orElseThrow();
        assertFalse(e.income());
        assertEquals(new BigDecimal("250"), e.amount());
        assertEquals("кофе", e.description());
    }

    @Test
    void amountThenDescription_isExpense() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("250 кофе с собой").orElseThrow();
        assertFalse(e.income());
        assertEquals("кофе с собой", e.description());
    }

    @Test
    void leadingPlus_isIncome() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("+5000 зарплата").orElseThrow();
        assertTrue(e.income());
        assertEquals(new BigDecimal("5000"), e.amount());
        assertEquals("зарплата", e.description());
    }

    @Test
    void trailingPlusDescription_isIncome() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("+зарплата 5000").orElseThrow();
        assertTrue(e.income());
        assertEquals("зарплата", e.description());
    }

    @Test
    void currencySuffixAndCommaDecimal_areHandled() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("такси 400,50р").orElseThrow();
        assertEquals(new BigDecimal("400.50"), e.amount());
        assertEquals("такси", e.description());
    }

    @Test
    void wordEndingWithR_isNotTreatedAsCurrency() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("бар 700").orElseThrow();
        assertEquals("бар", e.description());
        assertEquals(new BigDecimal("700"), e.amount());
    }

    @Test
    void amountOnly_getsDefaultDescription() {
        ExpenseTextParser.Entry e = ExpenseTextParser.parse("300").orElseThrow();
        assertEquals("расход", e.description());
    }

    @Test
    void unparsableOrZero_isRejected() {
        assertEquals(Optional.empty(), ExpenseTextParser.parse("привет"));
        assertEquals(Optional.empty(), ExpenseTextParser.parse("кофе 0"));
        assertEquals(Optional.empty(), ExpenseTextParser.parse("   "));
        assertEquals(Optional.empty(), ExpenseTextParser.parse(null));
    }
}
