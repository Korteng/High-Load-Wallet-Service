package ru.korteng.wallet.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.korteng.wallet.llm.LlmInputSanitizer;
import ru.korteng.wallet.telegram.Assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only assistant. Step 1: the model chooses an action (whitelisted). Step 2: the application runs it for the
 * chat's own wallet. Step 3: the model phrases the answer from the found data; if that fails, a template is used.
 * The model never sees data it did not ask for and cannot choose the wallet or write anything.
 */
@Service
@ConditionalOnProperty(name = "llm.enabled", havingValue = "true")
public class AssistantService implements Assistant {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int MAX_QUESTION = 300;
    private static final String FAILURE = "Не получилось ответить, попробуйте позже.";
    private static final String OUT_OF_SCOPE =
            "Я отвечаю на вопросы про баланс, расходы и последние операции. Например: «сколько я потратил на еду за неделю?»";
    private static final Set<String> CATEGORIES = Set.of("еда", "транспорт", "жильё", "здоровье", "развлечения", "прочее");

    private final AssistantModel model;
    private final WalletTools tools;

    public AssistantService(AssistantModel model, WalletTools tools) {
        this.model = model;
        this.tools = tools;
    }

    @Override
    public String ask(long chatId, String question) {
        String clean = LlmInputSanitizer.sanitize(question, MAX_QUESTION);
        try {
            AssistantModel.Plan plan = plan(clean);
            if (plan == null || plan.action() == null) {
                return OUT_OF_SCOPE;
            }
            ToolContext context = new ToolContext(Map.of(WalletTools.CHAT_ID_KEY, chatId));
            Object data;
            String fallback;
            switch (plan.action().trim().toLowerCase()) {
                case "balance" -> {
                    WalletTools.Balance balance = tools.getBalance(context);
                    data = balance;
                    fallback = "Баланс: " + balance.rub() + " ₽";
                }
                case "spending" -> {
                    int days = plan.days() == null ? 30 : plan.days();
                    String category = plan.category() == null ? null : plan.category().trim().toLowerCase();
                    if (category != null && !CATEGORIES.contains(category)) {
                        category = null;
                    }
                    WalletTools.SpendingReport report = tools.getSpending(days, category, context);
                    data = report;
                    fallback = spendingText(report, category);
                }
                case "history" -> {
                    int limit = plan.limit() == null ? 5 : plan.limit();
                    List<WalletTools.OperationView> ops = tools.getRecentOperations(limit, context);
                    data = ops;
                    fallback = ops.isEmpty() ? "Операций пока нет." : historyText(ops);
                }
                default -> {
                    return OUT_OF_SCOPE;
                }
            }
            return phrase(clean, data, fallback);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            log.warn("Assistant failed: {}: {}", e.getClass().getSimpleName(),
                    message.length() > 300 ? message.substring(0, 300) : message);
            log.debug("Assistant failure details", e);
            return FAILURE;
        }
    }

    /** The model sometimes returns an empty body; retry once, then fall back to rules. */
    private AssistantModel.Plan plan(String question) {
        String today = LocalDate.now(ZoneId.of("Europe/Moscow")).toString();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                AssistantModel.Plan plan = model.plan(question, today);
                if (plan != null && plan.action() != null) {
                    return plan;
                }
            } catch (RuntimeException e) {
                log.warn("Assistant planning attempt {} failed: {}", attempt, e.getClass().getSimpleName());
            }
        }
        log.warn("Assistant planning failed twice, using keyword planner");
        return KeywordPlanner.plan(question);
    }

    private String phrase(String question, Object data, String fallback) {
        try {
            return model.phrase(question, JSON.writeValueAsString(data)).orElse(fallback);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Assistant phrasing failed, using template: {}", e.getClass().getSimpleName());
            return fallback;
        }
    }

    private static String spendingText(WalletTools.SpendingReport report, String category) {
        String scope = category == null ? "" : " на «" + category + "»";
        if (report.totalRub().compareTo(BigDecimal.ZERO) == 0) {
            return "За " + report.days() + " дн. расходов" + scope + " нет.";
        }
        String text = "За " + report.days() + " дн. потрачено" + scope + ": " + report.totalRub() + " ₽";
        if (category == null && !report.byCategory().isEmpty()) {
            text += "\n" + report.byCategory().stream()
                    .map(s -> s.category() + ": " + s.rub() + " ₽")
                    .collect(Collectors.joining("\n"));
        }
        return text;
    }

    private static String historyText(List<WalletTools.OperationView> ops) {
        return ops.stream()
                .map(o -> (o.type().equals("DEPOSIT") ? "+" : "-") + o.rub() + " ₽ "
                        + (o.category() == null ? "" : o.category() + " ")
                        + (o.description() == null ? "" : o.description()))
                .collect(Collectors.joining("\n"));
    }
}
