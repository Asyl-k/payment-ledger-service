package dev.asyl.portfolio.payments;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class PaymentService {
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    public PaymentService(AccountRepository accounts,TransferRepository transfers){this.accounts=accounts;this.transfers=transfers;}
    @Transactional
    public Transfer transfer(String key,String from,String to,BigDecimal amount){
        if(key==null||key.isBlank())throw new IllegalArgumentException("Idempotency-Key is required");
        if(from.equals(to))throw new IllegalArgumentException("Source and target accounts must differ");
        if(amount==null||amount.scale()>2||amount.signum()<=0)throw new IllegalArgumentException("Amount must be positive with at most 2 decimals");
        var existing=transfers.findByIdempotencyKey(key);
        if(existing.isPresent()){
            if(!existing.get().matches(from,to,amount))throw new IdempotencyConflictException(key);
            return existing.get();
        }
        Map<String,Account> locked=new HashMap<>();
        StreamOrder.of(from,to).forEach(id->locked.put(id,accounts.findByIdForUpdate(id).orElseThrow(()->new AccountNotFoundException(id))));
        locked.get(from).debit(amount);
        locked.get(to).credit(amount);
        accounts.saveAll(locked.values());
        return transfers.save(new Transfer(key,from,to,amount));
    }
    private static class StreamOrder {
        static java.util.stream.Stream<String> of(String a,String b){return java.util.stream.Stream.of(a,b).sorted();}
    }
    public static class AccountNotFoundException extends RuntimeException{public AccountNotFoundException(String id){super("Account not found: "+id);}}
    public static class IdempotencyConflictException extends RuntimeException{public IdempotencyConflictException(String key){super("Idempotency key reused with a different transfer: "+key);}}
}
