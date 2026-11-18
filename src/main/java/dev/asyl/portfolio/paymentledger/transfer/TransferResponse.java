package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.account.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long id,
        String idempotencyKey,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Currency currency,
        Instant createdAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getIdempotencyKey(),
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getCreatedAt()
        );
    }
}
