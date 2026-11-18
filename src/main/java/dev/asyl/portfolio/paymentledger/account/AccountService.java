package dev.asyl.portfolio.paymentledger.account;

import dev.asyl.portfolio.paymentledger.account.AccountDtos.AccountResponse;
import dev.asyl.portfolio.paymentledger.account.AccountDtos.CreateAccountRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        Account account = new Account(request.ownerName().trim(), request.currency());
        return AccountResponse.from(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        return AccountResponse.from(requireVisibleAccount(id));
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> list(Pageable pageable) {
        return accounts.findBySystemAccountFalse(pageable).map(AccountResponse::from);
    }

    private Account requireVisibleAccount(Long id) {
        return accounts.findById(id)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
