package ru.korteng.wallet.telegram;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class KeywordCategoryClassifier implements CategoryClassifier {

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("еда", List.of("кофе", "обед", "ужин", "завтрак", "продукт", "магазин", "пятёрочка", "пятерочка",
                "магнит", "кафе", "ресторан", "пицц", "доставка"));
        KEYWORDS.put("транспорт", List.of("такси", "метро", "бензин", "автобус", "проезд", "парковк", "заправк"));
        KEYWORDS.put("жильё", List.of("аренда", "квартир", "коммунал", "жкх", "электричеств", "интернет"));
        KEYWORDS.put("здоровье", List.of("аптек", "врач", "лекарств", "стоматолог", "анализ"));
        KEYWORDS.put("развлечения", List.of("кино", "игр", "подписк", "steam", "театр", "концерт"));
    }

    @Override
    public String classify(String description, boolean income) {
        if (income) {
            return "доход";
        }
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "прочее";
    }
}
