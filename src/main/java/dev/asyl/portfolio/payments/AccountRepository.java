package dev.asyl.portfolio.payments;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface AccountRepository extends JpaRepository<Account,String>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id=:id")
    Optional<Account> findByIdForUpdate(@Param("id") String id);
}
