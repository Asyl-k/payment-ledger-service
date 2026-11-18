package dev.asyl.portfolio.paymentledger.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long id) {
        super("Account %d not found".formatted(id));
    }
}
