package ru.korteng.wallet.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.korteng.wallet.model.TelegramUser;
import ru.korteng.wallet.model.Wallet;
import ru.korteng.wallet.repository.TelegramUserRepository;
import ru.korteng.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps a Telegram chat to exactly one wallet. The bot only ever touches the wallet linked to the chat,
 * so a user cannot reach somebody else's wallet through the bot.
 */
@Service
@Transactional
public class TelegramUserService {

    private final TelegramUserRepository repository;
    private final WalletService walletService;

    public TelegramUserService(TelegramUserRepository repository, WalletService walletService) {
        this.repository = repository;
        this.walletService = walletService;
    }

    public UUID getOrCreateWalletId(long chatId) {
        return repository.findById(chatId)
                .map(TelegramUser::getWalletId)
                .orElseGet(() -> {
                    Wallet wallet = walletService.createWallet(BigDecimal.ZERO);
                    repository.save(new TelegramUser(chatId, wallet.getId()));
                    return wallet.getId();
                });
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findWalletId(long chatId) {
        return repository.findById(chatId).map(TelegramUser::getWalletId);
    }
}
