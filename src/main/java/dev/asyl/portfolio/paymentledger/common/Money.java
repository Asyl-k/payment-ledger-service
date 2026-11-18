package dev.asyl.portfolio.paymentledger.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal normalize(BigDecimal value) {
        try {
            return scale(value);
        } catch (ArithmeticException ex) {
            throw new InvalidAmountException("Amount must have at most two decimal places");
        }
    }
}
