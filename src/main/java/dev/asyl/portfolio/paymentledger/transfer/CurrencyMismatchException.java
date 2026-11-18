package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.account.Currency;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency source, Currency target) {
        super("Account currencies do not match: %s and %s".formatted(source, target));
    }
}
