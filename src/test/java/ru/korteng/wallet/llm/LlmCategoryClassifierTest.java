package ru.korteng.wallet.llm;

import org.junit.jupiter.api.Test;
import ru.korteng.wallet.telegram.KeywordCategoryClassifier;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LlmCategoryClassifierTest {

    private final KeywordCategoryClassifier keywords = new KeywordCategoryClassifier();

    @Test
    void keywordHit_doesNotCallLlm() {
        AtomicInteger calls = new AtomicInteger();
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> {
            calls.incrementAndGet();
            return Optional.of("транспорт");
        });

        assertEquals("еда", classifier.classify("кофе", false));
        assertEquals(0, calls.get());
    }

    @Test
    void income_doesNotCallLlm() {
        AtomicInteger calls = new AtomicInteger();
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> {
            calls.incrementAndGet();
            return Optional.of("еда");
        });

        assertEquals("доход", classifier.classify("премия", true));
        assertEquals(0, calls.get());
    }

    @Test
    void unknownExpense_usesLlmAnswer() {
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> Optional.of(" Здоровье "));

        assertEquals("здоровье", classifier.classify("массаж спины", false));
    }

    @Test
    void answerOutsideKnownCategories_fallsBackToKeywordResult() {
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> Optional.of("взлом"));

        assertEquals("прочее", classifier.classify("массаж спины", false));
    }

    @Test
    void llmFailure_fallsBackToKeywordResult() {
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> {
            throw new IllegalStateException("timeout");
        });

        assertEquals("прочее", classifier.classify("массаж спины", false));
    }

    @Test
    void sameDescription_isAskedOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> {
            calls.incrementAndGet();
            return Optional.of("развлечения");
        });

        assertEquals("развлечения", classifier.classify("боулинг", false));
        assertEquals("развлечения", classifier.classify("Боулинг", false));
        assertEquals(1, calls.get());
    }

    @Test
    void descriptionSentToLlm_isSanitized() {
        AtomicReference<String> seen = new AtomicReference<>();
        LlmCategoryClassifier classifier = new LlmCategoryClassifier(keywords, d -> {
            seen.set(d);
            return Optional.of("прочее");
        });

        classifier.classify("перевод +7 918 139-30-70 ivan@mail.ru", false);

        assertFalse(seen.get().contains("918"));
        assertFalse(seen.get().contains("ivan@mail.ru"));
        assertEquals("перевод [номер] [email]", seen.get());
    }
}
