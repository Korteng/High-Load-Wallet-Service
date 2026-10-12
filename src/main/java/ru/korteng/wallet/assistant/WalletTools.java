package ru.korteng.wallet.assistant;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.korteng.wallet.dto.WalletRequest.OperationType;
import ru.korteng.wallet.llm.LlmInputSanitizer;
import ru.korteng.wallet.model.WalletOperation;
import ru.korteng.wallet.repository.WalletOperationRepository;
import ru.korteng.wallet.service.WalletService;
import ru.korteng.wallet.telegram.TelegramUserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only tools the model may call. The wallet is taken from the chat id in the tool context, which the
 * application sets; the model has no parameter to choose a wallet, so it cannot reach somebody else's data.
 * Tool results are sent to the LLM provider, so operation descriptions are sanitized here as well.
 */
@Component
@ConditionalOnProperty(name = "llm.enabled", havingValue = "true")
public class WalletTools {

    public static final String CHAT_ID_KEY = "chatId";

    public record Balance(BigDecimal rub) {
    }

    public record CategorySum(String category, BigDecimal rub) {
    }

    public record SpendingReport(int days, BigDecimal totalRub, List<CategorySum> byCategory) {
    }

    public record OperationView(String date, String type, BigDecimal rub, String category, String description) {
    }

    private final TelegramUserService users;
    private final WalletService wallets;
    private final WalletOperationRepository operations;

    public WalletTools(TelegramUserService users, WalletService wallets, WalletOperationRepository operations) {
        this.users = users;
        this.wallets = wallets;
        this.operations = operations;
    }

    @Tool(description = "Текущий баланс кошелька пользователя в рублях.")
    public Balance getBalance(ToolContext toolContext) {
        return new Balance(wallets.getWallet(walletId(toolContext)).getBalance());
    }

    @Tool(description = "Сумма расходов пользователя в рублях за последние N дней: итог и разбивка по категориям. "
            + "Категории: еда, транспорт, жильё, здоровье, развлечения, прочее.")
    public SpendingReport getSpending(
            @ToolParam(description = "За сколько последних дней, от 1 до 365") int days,
            @ToolParam(description = "Только эта категория; не указывать, если нужны все", required = false)
            String category,
            ToolContext toolContext) {
        int period = Math.max(1, Math.min(days, 365));
        Instant from = Instant.now().minus(period, ChronoUnit.DAYS);
        String wanted = category == null || category.isBlank() ? null : category.trim().toLowerCase(Locale.ROOT);

        List<CategorySum> sums = operations.sumByCategory(walletId(toolContext), OperationType.WITHDRAW, from).stream()
                .map(row -> new CategorySum(row.getCategory() == null ? "без категории" : row.getCategory(),
                        row.getTotal()))
                .filter(row -> wanted == null || row.category().equals(wanted))
                .toList();
        BigDecimal total = sums.stream().map(CategorySum::rub).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SpendingReport(period, total, sums);
    }

    @Tool(description = "Последние операции пользователя: дата, тип (DEPOSIT или WITHDRAW), сумма, категория, описание.")
    public List<OperationView> getRecentOperations(
            @ToolParam(description = "Сколько операций вернуть, от 1 до 20") int limit,
            ToolContext toolContext) {
        int count = Math.max(1, Math.min(limit, 20));
        return wallets.getHistory(walletId(toolContext), count).stream()
                .map(WalletTools::view)
                .toList();
    }

    private static OperationView view(WalletOperation op) {
        return new OperationView(op.getCreatedAt().toString(), op.getOperationType().name(), op.getAmount(),
                op.getCategory(), LlmInputSanitizer.sanitize(op.getDescription(), 100));
    }

    private UUID walletId(ToolContext toolContext) {
        Object chatId = toolContext == null ? null : toolContext.getContext().get(CHAT_ID_KEY);
        if (!(chatId instanceof Long id)) {
            throw new IllegalStateException("No chat in the tool context");
        }
        return users.findWalletId(id).orElseThrow(() -> new IllegalStateException("Wallet is not linked to the chat"));
    }
}
