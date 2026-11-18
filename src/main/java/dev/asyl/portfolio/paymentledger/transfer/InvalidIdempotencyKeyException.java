package dev.asyl.portfolio.paymentledger.transfer;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("The Idempotency-Key header must contain 8 to 64 characters: letters, digits, '.', '_', '-' or ':'");
    }
}
