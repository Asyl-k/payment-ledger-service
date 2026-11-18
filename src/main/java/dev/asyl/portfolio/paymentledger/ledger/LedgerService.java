package dev.asyl.portfolio.paymentledger.ledger;

import dev.asyl.portfolio.paymentledger.account.Account;
import dev.asyl.portfolio.paymentledger.account.AccountNotFoundException;
import dev.asyl.portfolio.paymentledger.account.AccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    public LedgerService(AccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> listForAccount(Long accountId, Pageable pageable) {
        requireVisibleAccount(accountId);
        return entries.findByAccountId(accountId, pageable).map(LedgerEntryResponse::from);
    }

    private Account requireVisibleAccount(Long accountId) {
        return accounts.findById(accountId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
