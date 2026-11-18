package dev.asyl.portfolio.paymentledger.account;

import dev.asyl.portfolio.paymentledger.transfer.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void creditIncreasesBalance() {
        Account account = new Account("Alice", Currency.USD);

        account.credit(new BigDecimal("25.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("25.50");
    }

    @Test
    void debitDecreasesBalance() {
        Account account = new Account("Alice", Currency.USD);
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("40.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("59.75");
    }

    @Test
    void debitBeyondBalanceIsRejected() {
        Account account = new Account("Alice", Currency.USD);
        account.credit(new BigDecimal("10.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient funds");
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void fundingAccountMayGoNegative() {
        Account funding = new Account("External funding", Currency.USD);
        ReflectionTestUtils.setField(funding, "systemAccount", true);

        funding.debit(new BigDecimal("50.00"));

        assertThat(funding.getBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Account account = new Account("Alice", Currency.USD);

        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
