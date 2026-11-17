package dev.asyl.portfolio.payments;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="transfers",uniqueConstraints=@UniqueConstraint(columnNames="idempotency_key"))
public class Transfer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="idempotency_key",nullable=false,updatable=false,length=100)
    private String idempotencyKey;
    @Column(name="source_account",nullable=false,length=80)
    private String sourceAccount;
    @Column(name="target_account",nullable=false,length=80)
    private String targetAccount;
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal amount;
    @Column(nullable=false,updatable=false)
    private Instant createdAt=Instant.now();
    protected Transfer(){}
    public Transfer(String key,String from,String to,BigDecimal amount){this.idempotencyKey=key;this.sourceAccount=from;this.targetAccount=to;this.amount=amount;}
    public Long getId(){return id;}
    public String getIdempotencyKey(){return idempotencyKey;}
    public String getSourceAccount(){return sourceAccount;}
    public String getTargetAccount(){return targetAccount;}
    public BigDecimal getAmount(){return amount;}
    public Instant getCreatedAt(){return createdAt;}
    public boolean matches(String from,String to,BigDecimal value){return sourceAccount.equals(from)&&targetAccount.equals(to)&&amount.compareTo(value)==0;}
}
