package ru.korteng.wallet.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.korteng.wallet.dto.WalletRequest;
import ru.korteng.wallet.dto.WalletRequest.OperationType;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.exception.InsufficientFundsException;
import ru.korteng.wallet.model.WalletOperation;
import ru.korteng.wallet.service.WalletService;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transport-independent bot logic: takes a chat message, returns the reply text (or null for no reply).
 */
@Component
public class BotCommandHandler {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Moscow"));

    private static final String HELP = """
            Команды:
            /balance - баланс
            /history - последние 10 операций
            /help - справка

            Запись операций обычным текстом:
            кофе 250 - расход
            такси 400р - расход
            +5000 зарплата - пополнение""";

    private final WalletService walletService;
    private final TelegramUserService telegramUserService;
    private final CategoryClassifier classifier;
    private final Set<Long> allowedChatIds;

    private final Optional<Assistant> assistant;

    public BotCommandHandler(WalletService walletService,
                             TelegramUserService telegramUserService,
                             CategoryClassifier classifier,
                             String allowedChatIds) {
        this(walletService, telegramUserService, classifier, allowedChatIds, Optional.empty());
    }

    @Autowired
    public BotCommandHandler(WalletService walletService,
                             TelegramUserService telegramUserService,
                             CategoryClassifier classifier,
                             @Value("${telegram.allowed-chat-ids:}") String allowedChatIds,
                             Optional<Assistant> assistant) {
        this.walletService = walletService;
        this.telegramUserService = telegramUserService;
        this.classifier = classifier;
        this.allowedChatIds = parseAllowed(allowedChatIds);
        this.assistant = assistant;
    }

    public String handle(long chatId, long updateId, String rawText) {
        if (!allowedChatIds.isEmpty() && !allowedChatIds.contains(chatId)) {
            return "Доступ ограничен.";
        }
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("/")) {
            return handleCommand(chatId, text);
        }
        return handleEntry(chatId, updateId, text);
    }

    private String handleCommand(long chatId, String text) {
        String command = text.split("\\s+")[0].toLowerCase(Locale.ROOT);
        int at = command.indexOf('@');
        if (at > 0) {
            command = command.substring(0, at);
        }
        return switch (command) {
            case "/start" -> {
                UUID walletId = telegramUserService.getOrCreateWalletId(chatId);
                yield "Кошелёк готов. Баланс: " + format(walletService.getWallet(walletId).getBalance()) + "\n\n" + HELP;
            }
            case "/help" -> HELP;
            case "/balance" -> withWallet(chatId, walletId ->
                    "Баланс: " + format(walletService.getWallet(walletId).getBalance()));
            case "/history" -> withWallet(chatId, this::historyText);
            default -> "Неизвестная команда. /help";
        };
    }

    private String handleEntry(long chatId, long updateId, String text) {
        if (assistant.isPresent() && looksLikeQuestion(text)) {
            return withWallet(chatId, walletId -> assistant.get().ask(chatId, text));
        }
        Optional<ExpenseTextParser.Entry> parsed = ExpenseTextParser.parse(text);
        if (parsed.isEmpty()) {
            return "Не понял. Пример: «кофе 250» или «+5000 зарплата». /help";
        }
        return withWallet(chatId, walletId -> {
            ExpenseTextParser.Entry entry = parsed.get();
            OperationType type = entry.income() ? OperationType.DEPOSIT : OperationType.WITHDRAW;
            String category = classifier.classify(entry.description(), entry.income());
            String key = "tg-" + chatId + "-" + updateId;
            try {
                WalletResponse response = walletService.createOperation(
                        new WalletRequest(walletId, type, entry.amount()), category, entry.description(), key);
                String sign = entry.income() ? "+" : "-";
                return "Записал: " + sign + format(entry.amount()) + " (" + category + "). Баланс: "
                        + format(response.getNewBalance());
            } catch (InsufficientFundsException e) {
                return e.getMessage();
            }
        });
    }

    private static final List<String> QUESTION_WORDS = List.of("сколько", "какой", "какая", "какие", "покажи",
            "на что", "где", "когда", "что", "почему", "много ли", "хватит");

    static boolean looksLikeQuestion(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.contains("?")
                || QUESTION_WORDS.stream().anyMatch(word -> lower.equals(word) || lower.startsWith(word + " "));
    }

    private String historyText(UUID walletId) {
        List<WalletOperation> operations = walletService.getHistory(walletId, 10);
        if (operations.isEmpty()) {
            return "Операций пока нет.";
        }
        return operations.stream()
                .map(op -> TIME_FORMAT.format(op.getCreatedAt()) + "  "
                        + (op.getOperationType() == OperationType.DEPOSIT ? "+" : "-") + format(op.getAmount())
                        + "  " + op.getDescription()
                        + (op.getCategory() != null ? " (" + op.getCategory() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }

    private String withWallet(long chatId, java.util.function.Function<UUID, String> action) {
        return telegramUserService.findWalletId(chatId)
                .map(action)
                .orElse("Сначала отправьте /start.");
    }

    private static String format(java.math.BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Set<Long> parseAllowed(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
