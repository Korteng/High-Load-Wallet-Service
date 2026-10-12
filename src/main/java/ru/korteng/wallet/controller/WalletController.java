package ru.korteng.wallet.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.korteng.wallet.dto.OperationResponse;
import ru.korteng.wallet.dto.WalletRequest;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.model.Wallet;
import ru.korteng.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/wallet")
public class WalletController {
    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestParam(required = false, defaultValue = "0") BigDecimal initialBalance) {
        Wallet wallet = service.createWallet(initialBalance);
        return ResponseEntity.ok(new WalletResponse(wallet.getId(), wallet.getBalance()));
    }

    @PostMapping("/balance")
    public ResponseEntity<WalletResponse> updateBalance(@Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(service.createOperation(request));
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<WalletResponse> getBalance(@PathVariable UUID walletId) {
        Wallet wallet = service.getWallet(walletId);
        return ResponseEntity.ok(new WalletResponse(wallet.getId(), wallet.getBalance()));
    }

    @GetMapping("/wallets/{walletId}/operations")
    public ResponseEntity<List<OperationResponse>> getOperations(@PathVariable UUID walletId,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        List<OperationResponse> history = service.getHistory(walletId, limit).stream()
                .map(OperationResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
