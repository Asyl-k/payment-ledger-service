package dev.asyl.portfolio.paymentledger.transfer;

public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("The Idempotency-Key header is required");
    }
}
