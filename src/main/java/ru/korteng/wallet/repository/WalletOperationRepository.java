package ru.korteng.wallet.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.korteng.wallet.model.WalletOperation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletOperationRepository extends JpaRepository<WalletOperation, UUID> {

    List<WalletOperation> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Optional<WalletOperation> findByIdempotencyKey(String idempotencyKey);
}
