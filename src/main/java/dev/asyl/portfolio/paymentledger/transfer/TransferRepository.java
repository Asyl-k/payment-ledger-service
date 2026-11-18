package dev.asyl.portfolio.paymentledger.transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @EntityGraph(attributePaths = {"fromAccount", "toAccount"})
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    Page<Transfer> findByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId, Pageable pageable);
}
