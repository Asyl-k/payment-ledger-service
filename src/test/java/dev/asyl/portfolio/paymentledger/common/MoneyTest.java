package dev.asyl.portfolio.paymentledger.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesToTwoDecimalPlaces() {
        assertThat(Money.normalize(new BigDecimal("10")))
                .isEqualByComparingTo("10.00");
        assertThat(Money.normalize(new BigDecimal("10.5")))
                .isEqualByComparingTo("10.50");
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("10.001")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("two decimal places");
    }
}
