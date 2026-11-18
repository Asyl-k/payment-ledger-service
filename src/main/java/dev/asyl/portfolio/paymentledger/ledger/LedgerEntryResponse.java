package dev.asyl.portfolio.paymentledger.ledger;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        Long id,
        Long transferId,
        Long accountId,
        LedgerDirection direction,
        BigDecimal amount,
        Instant createdAt
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransfer().getId(),
                entry.getAccount().getId(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCreatedAt()
        );
    }
}
