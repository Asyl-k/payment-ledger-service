package dev.asyl.portfolio.paymentledger.common;

public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
