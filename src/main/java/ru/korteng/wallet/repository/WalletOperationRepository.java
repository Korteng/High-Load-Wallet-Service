package ru.korteng.wallet.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.korteng.wallet.dto.WalletRequest.OperationType;
import ru.korteng.wallet.model.WalletOperation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletOperationRepository extends JpaRepository<WalletOperation, UUID> {

    List<WalletOperation> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Optional<WalletOperation> findByIdempotencyKey(String idempotencyKey);

    interface CategoryTotal {
        String getCategory();

        BigDecimal getTotal();
    }

    @Query("""
            select o.category as category, sum(o.amount) as total
            from WalletOperation o
            where o.walletId = :walletId and o.operationType = :type and o.createdAt >= :from
            group by o.category
            order by sum(o.amount) desc
            """)
    List<CategoryTotal> sumByCategory(@Param("walletId") UUID walletId,
                                      @Param("type") OperationType type,
                                      @Param("from") Instant from);
}
