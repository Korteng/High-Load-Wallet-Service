package ru.korteng.wallet.llm;

import java.util.regex.Pattern;

/**
 * Everything that leaves for the LLM provider goes through here: long digit sequences (phone/card numbers)
 * and emails are masked and the text is cut. Names and addresses are NOT detected.
 */
public final class LlmInputSanitizer {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\+?\\d[\\d\\s()-]{5,}\\d");

    private LlmInputSanitizer() {
    }

    public static String sanitize(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String result = EMAIL.matcher(text).replaceAll("[email]");
        result = LONG_NUMBER.matcher(result).replaceAll("[номер]");
        result = result.trim();
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }
}
