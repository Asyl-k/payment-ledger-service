package dev.asyl.portfolio.paymentledger.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from LedgerEntry e
            where e.account.id = :accountId and e.direction = :direction
            """)
    BigDecimal sumByAccountAndDirection(@Param("accountId") Long accountId,
                                        @Param("direction") LedgerDirection direction);
}
