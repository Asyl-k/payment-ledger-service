package dev.asyl.portfolio.paymentledger.transfer;

public class SystemAccountRestrictedException extends RuntimeException {

    public SystemAccountRestrictedException() {
        super("The external funding account can only be used through the deposit endpoint");
    }
}
