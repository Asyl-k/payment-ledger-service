package dev.asyl.portfolio.paymentledger.transfer;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long accountId, BigDecimal balance, BigDecimal requested) {
        super("Account %s has insufficient funds: balance %s, requested %s"
                .formatted(accountId, balance, requested));
    }
}
