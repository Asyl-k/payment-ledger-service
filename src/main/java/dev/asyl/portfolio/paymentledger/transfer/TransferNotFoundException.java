package dev.asyl.portfolio.paymentledger.transfer;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(Long id) {
        super("Transfer %d not found".formatted(id));
    }
}
