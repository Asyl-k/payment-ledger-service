package dev.asyl.portfolio.paymentledger.transfer;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency key '%s' was already used with a different payload".formatted(key));
    }
}
