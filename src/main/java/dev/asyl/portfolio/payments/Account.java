package dev.asyl.portfolio.payments;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="accounts")
public class Account {
    @Id @Column(length=80)
    private String id;
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal balance=BigDecimal.ZERO;
    protected Account(){}
    public Account(String id,BigDecimal openingBalance){
        if(openingBalance.signum()<0)throw new IllegalArgumentException("Opening balance cannot be negative");
        this.id=id;this.balance=openingBalance;
    }
    public String getId(){return id;}
    public BigDecimal getBalance(){return balance;}
    public void debit(BigDecimal amount){
        validateAmount(amount);
        if(balance.compareTo(amount)<0)throw new InsufficientFundsException(id);
        balance=balance.subtract(amount);
    }
    public void credit(BigDecimal amount){
        validateAmount(amount);
        balance=balance.add(amount);
    }
    private void validateAmount(BigDecimal amount){
        if(amount==null||amount.signum()<=0||amount.scale()>2)
            throw new IllegalArgumentException("Amount must be positive with at most 2 decimals");
    }
    public static class InsufficientFundsException extends RuntimeException{
        public InsufficientFundsException(String id){super("Insufficient funds in account "+id);}
    }
}
