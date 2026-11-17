package dev.asyl.portfolio.payments;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class AccountTest {
    @Test void debitAndCreditPreserveMoney(){
        var account=new Account("a-1",new BigDecimal("20.00"));
        account.debit(new BigDecimal("7.25"));
        account.credit(new BigDecimal("2.00"));
        assertEquals(new BigDecimal("14.75"),account.getBalance());
    }
    @Test void cannotOverdraw(){
        var account=new Account("a-1",new BigDecimal("5.00"));
        assertThrows(Account.InsufficientFundsException.class,()->account.debit(new BigDecimal("5.01")));
        assertEquals(new BigDecimal("5.00"),account.getBalance());
    }
    @Test void rejectSubCentAndNonPositiveAmounts(){
        var account=new Account("a-1",new BigDecimal("5.00"));
        assertThrows(IllegalArgumentException.class,()->account.debit(new BigDecimal("0.001")));
        assertThrows(IllegalArgumentException.class,()->account.credit(BigDecimal.ZERO));
    }
}
