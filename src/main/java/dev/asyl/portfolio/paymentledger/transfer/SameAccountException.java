package dev.asyl.portfolio.paymentledger.transfer;

public class SameAccountException extends RuntimeException {

    public SameAccountException() {
        super("Source and destination accounts must be different");
    }
}
