package ru.korteng.wallet.assistant;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Rule-based plan used when the model returns nothing usable. Recognizes only the obvious phrasings. */
final class KeywordPlanner {

    private static final Map<String, String> CATEGORY_STEMS = new LinkedHashMap<>();

    static {
        CATEGORY_STEMS.put("ед", "еда");
        CATEGORY_STEMS.put("продукт", "еда");
        CATEGORY_STEMS.put("транспорт", "транспорт");
        CATEGORY_STEMS.put("такси", "транспорт");
        CATEGORY_STEMS.put("жиль", "жильё");
        CATEGORY_STEMS.put("здоровь", "здоровье");
        CATEGORY_STEMS.put("развлечен", "развлечения");
    }

    private KeywordPlanner() {
    }

    static AssistantModel.Plan plan(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (q.contains("последн") || q.contains("истори") || q.contains("операци")) {
            return new AssistantModel.Plan("history", null, null, number(q, 5));
        }
        if (q.contains("потрат") || q.contains("расход") || q.contains("трат")) {
            return new AssistantModel.Plan("spending", days(q), category(q), null);
        }
        if (q.contains("баланс") || q.contains("остаток") || q.contains("сколько денег")) {
            return new AssistantModel.Plan("balance", null, null, null);
        }
        return new AssistantModel.Plan("none", null, null, null);
    }

    private static int days(String q) {
        if (q.contains("сегодня")) return 1;
        if (q.contains("недел")) return 7;
        if (q.contains("год")) return 365;
        return 30;
    }

    private static String category(String q) {
        for (Map.Entry<String, String> e : CATEGORY_STEMS.entrySet()) {
            String stem = e.getKey();
            // short stems must start a word, otherwise "ед" would match inside any word ("победа")
            if (stem.length() <= 2
                    ? java.util.regex.Pattern.compile("(^|[^а-я])" + stem + "(а|у|ы|е|ой)?([^а-я]|$)").matcher(q).find()
                    : q.contains(stem)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static int number(String q, int fallback) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{1,2}").matcher(q);
        return m.find() ? Integer.parseInt(m.group()) : fallback;
    }
}
