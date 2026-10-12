package ru.korteng.wallet.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import ru.korteng.wallet.telegram.CategoryClassifier;
import ru.korteng.wallet.telegram.KeywordCategoryClassifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keywords first (free, instant, nothing leaves the machine). The language model is asked only when the
 * keyword classifier could not place an expense. The answer is accepted only if it is one of the known
 * categories, so a wrong, malformed or injected answer cannot create a new category. Any failure falls
 * back to the keyword result. Only a sanitized description is sent: no amounts, ids or chat data.
 */
@Component
@Primary
@ConditionalOnProperty(name = "llm.enabled", havingValue = "true")
public class LlmCategoryClassifier implements CategoryClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmCategoryClassifier.class);

    static final String DEFAULT_CATEGORY = "прочее";
    static final Set<String> ALLOWED = Set.of("еда", "транспорт", "жильё", "здоровье", "развлечения", DEFAULT_CATEGORY);

    private static final int MAX_DESCRIPTION = 100;
    private static final int CACHE_SIZE = 500;

    private final KeywordCategoryClassifier keywords;
    private final CategoryLlm llm;

    private final Map<String, String> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public LlmCategoryClassifier(KeywordCategoryClassifier keywords, CategoryLlm llm) {
        this.keywords = keywords;
        this.llm = llm;
    }

    @Override
    public String classify(String description, boolean income) {
        String base = keywords.classify(description, income);
        if (income || !DEFAULT_CATEGORY.equals(base) || description == null || description.isBlank()) {
            return base;
        }
        String clean = sanitize(description);
        String cached = cached(clean);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<String> suggestion = llm.suggest(clean);
            String category = suggestion.map(s -> s.trim().toLowerCase(Locale.ROOT)).orElse(null);
            if (category == null || !ALLOWED.contains(category)) {
                return base;
            }
            remember(clean, category);
            return category;
        } catch (Exception e) {
            log.warn("LLM categorization failed, using keyword result: {}", e.getClass().getSimpleName());
            log.debug("LLM categorization failure details", e);
            return base;
        }
    }

    static String sanitize(String description) {
        return LlmInputSanitizer.sanitize(description, MAX_DESCRIPTION);
    }

    private synchronized String cached(String key) {
        return cache.get(key.toLowerCase(Locale.ROOT));
    }

    private synchronized void remember(String key, String category) {
        cache.put(key.toLowerCase(Locale.ROOT), category);
    }
}
