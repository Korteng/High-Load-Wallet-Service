package ru.korteng.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.korteng.wallet.dto.WalletRequest;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.exception.InsufficientFundsException;
import ru.korteng.wallet.model.Wallet;
import ru.korteng.wallet.model.WalletOperation;
import ru.korteng.wallet.repository.WalletOperationRepository;
import ru.korteng.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.korteng.wallet.dto.WalletRequest.OperationType.DEPOSIT;
import static ru.korteng.wallet.dto.WalletRequest.OperationType.WITHDRAW;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletOperationRepository operationRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void deposit_shouldIncreaseBalance() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId, new BigDecimal("1000.00"));

        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(wallet)).thenReturn(wallet);

        WalletRequest request = new WalletRequest(walletId, DEPOSIT, new BigDecimal("500.00"));
        WalletResponse response = walletService.createOperation(request);

        assertEquals(new BigDecimal("1500.00"), response.getNewBalance());
    }

    @Test
    void operation_shouldBeRecordedInHistoryWithBalanceAfter() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId, new BigDecimal("1000.00"));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        walletService.createOperation(new WalletRequest(walletId, WITHDRAW, new BigDecimal("250.00")),
                "еда", "кофе", "key-1");

        verify(operationRepository).save(argThat(op ->
                op.getWalletId().equals(walletId)
                        && op.getOperationType() == WITHDRAW
                        && op.getAmount().compareTo(new BigDecimal("250.00")) == 0
                        && op.getBalanceAfter().compareTo(new BigDecimal("750.00")) == 0
                        && "еда".equals(op.getCategory())
                        && "key-1".equals(op.getIdempotencyKey())));
    }

    @Test
    void repeatedIdempotencyKey_shouldNotChangeBalanceAgain() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId, new BigDecimal("750.00"));
        WalletOperation original = WalletOperation.of(walletId, WITHDRAW, new BigDecimal("250.00"),
                new BigDecimal("750.00"), "еда", "кофе", "key-1");
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(operationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        WalletResponse response = walletService.createOperation(
                new WalletRequest(walletId, WITHDRAW, new BigDecimal("250.00")), "еда", "кофе", "key-1");

        assertEquals(new BigDecimal("750.00"), response.getNewBalance());
        assertEquals(new BigDecimal("750.00"), wallet.getBalance());
        verify(walletRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
    }

    @Test
    void withdrawMoreThanBalance_shouldFailAndRecordNothing() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId, new BigDecimal("100.00"));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientFundsException.class, () -> walletService.createOperation(
                new WalletRequest(walletId, WITHDRAW, new BigDecimal("300.00"))));

        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
        verify(operationRepository, never()).save(any());
    }
}
