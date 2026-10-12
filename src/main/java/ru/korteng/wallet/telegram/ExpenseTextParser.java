package ru.korteng.wallet.telegram;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-form chat messages like "кофе 250", "250 кофе", "+5000 зарплата" into an entry.
 * A leading "+" (before the amount or before the description) marks income, everything else is an expense.
 */
public final class ExpenseTextParser {

    public record Entry(boolean income, BigDecimal amount, String description) {
    }

    private static final String NUMBER = "(\\d+(?:[.,]\\d{1,2})?)";
    private static final Pattern LEADING = Pattern.compile("^([+-]?)\\s*" + NUMBER + "\\s*(.*)$");
    private static final Pattern TRAILING = Pattern.compile("^(.*?)\\s+([+-]?)" + NUMBER + "$");
    private static final Pattern CURRENCY_SUFFIX =
            Pattern.compile("(?iu)(?<=\\d)\\s*(?:₽|руб(?:\\.|лей|ля)?|р\\.?)\\s*$");

    private static final int MAX_DESCRIPTION = 255;

    private ExpenseTextParser() {
    }

    public static Optional<Entry> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = CURRENCY_SUFFIX.matcher(raw.trim()).replaceFirst("").trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        Matcher leading = LEADING.matcher(text);
        if (leading.matches()) {
            return build("+".equals(leading.group(1)), leading.group(2), leading.group(3));
        }

        Matcher trailing = TRAILING.matcher(text);
        if (trailing.matches()) {
            String description = trailing.group(1).trim();
            boolean income = "+".equals(trailing.group(2)) || description.startsWith("+");
            if (description.startsWith("+")) {
                description = description.substring(1).trim();
            }
            return build(income, trailing.group(3), description);
        }
        return Optional.empty();
    }

    private static Optional<Entry> build(boolean income, String number, String description) {
        BigDecimal amount = new BigDecimal(number.replace(',', '.'));
        if (amount.signum() <= 0) {
            return Optional.empty();
        }
        String desc = description == null ? "" : description.trim();
        if (desc.isEmpty()) {
            desc = income ? "пополнение" : "расход";
        }
        if (desc.length() > MAX_DESCRIPTION) {
            desc = desc.substring(0, MAX_DESCRIPTION);
        }
        return Optional.of(new Entry(income, amount, desc));
    }
}
