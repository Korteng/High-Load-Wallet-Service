package ru.korteng.wallet.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.korteng.wallet.dto.WalletRequest;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.exception.WalletNotFoundException;
import ru.korteng.wallet.model.Wallet;
import ru.korteng.wallet.model.WalletOperation;
import ru.korteng.wallet.repository.WalletOperationRepository;
import ru.korteng.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ru.korteng.wallet.dto.WalletRequest.OperationType.DEPOSIT;

@Service
@Transactional
public class WalletService {

    private final WalletRepository repository;
    private final WalletOperationRepository operationRepository;

    public WalletService(WalletRepository repository, WalletOperationRepository operationRepository) {
        this.repository = repository;
        this.operationRepository = operationRepository;
    }

    public WalletResponse createOperation(WalletRequest request) {
        return createOperation(request, null, null, null);
    }

    /**
     * Applies a deposit/withdraw under a row lock and records it in the operation history in the same transaction.
     * If an idempotency key is given and was already applied, the original result is returned and nothing changes.
     * The key is checked after the wallet row lock is acquired, so concurrent duplicates serialize on the lock.
     */
    public WalletResponse createOperation(WalletRequest request, String category, String description,
                                          String idempotencyKey) {
        Wallet wallet = repository.findByIdForUpdate(request.getId())
                .orElseThrow(() -> new WalletNotFoundException(request.getId()));

        if (idempotencyKey != null) {
            Optional<WalletOperation> existing = operationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new WalletResponse(wallet.getId(), existing.get().getBalanceAfter());
            }
        }

        if (request.getOperationType() == DEPOSIT) {
            wallet.deposit(request.getAmount());
        } else {
            wallet.withdraw(request.getAmount());
        }
        repository.save(wallet);
        operationRepository.save(WalletOperation.of(wallet.getId(), request.getOperationType(),
                request.getAmount(), wallet.getBalance(), category, description, idempotencyKey));
        return new WalletResponse(wallet.getId(), wallet.getBalance());
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<WalletOperation> getHistory(UUID walletId, int limit) {
        if (!repository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        int size = Math.max(1, Math.min(limit, 100));
        return operationRepository.findByWalletIdOrderByCreatedAtDesc(walletId, PageRequest.of(0, size));
    }

    public Wallet createWallet(BigDecimal initialBalance) {
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setBalance(initialBalance);
        return repository.save(wallet);
    }
}
