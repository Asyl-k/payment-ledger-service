package dev.asyl.portfolio.paymentledger.account;

import dev.asyl.portfolio.paymentledger.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 120) String ownerName,
            @NotNull Currency currency
    ) {
    }

    public record AccountResponse(
            Long id,
            String ownerName,
            Currency currency,
            BigDecimal balance,
            Instant createdAt
    ) {

        public static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getOwnerName(),
                    account.getCurrency(),
                    Money.scale(account.getBalance()),
                    account.getCreatedAt()
            );
        }
    }
}
