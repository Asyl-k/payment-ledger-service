package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.account.Account;
import dev.asyl.portfolio.paymentledger.account.AccountNotFoundException;
import dev.asyl.portfolio.paymentledger.account.AccountRepository;
import dev.asyl.portfolio.paymentledger.common.Money;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.CreateTransferRequest;
import dev.asyl.portfolio.paymentledger.ledger.LedgerDirection;
import dev.asyl.portfolio.paymentledger.ledger.LedgerEntry;
import dev.asyl.portfolio.paymentledger.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TransferExecutor {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledgerEntries;

    public TransferExecutor(AccountRepository accounts,
                            TransferRepository transfers,
                            LedgerEntryRepository ledgerEntries) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledgerEntries = ledgerEntries;
    }

    /**
     * Moves money between two accounts inside one database transaction.
     * Accounts are locked in ascending id order so that concurrent transfers
     * touching the same pair of accounts cannot deadlock.
     */
    @Transactional
    public Transfer execute(String idempotencyKey,
                            CreateTransferRequest request,
                            boolean fundingSourceAllowed) {
        long firstId = Math.min(request.fromAccountId(), request.toAccountId());
        long secondId = Math.max(request.fromAccountId(), request.toAccountId());

        Account first = accounts.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accounts.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account from = first.getId().equals(request.fromAccountId()) ? first : second;
        Account to = first.getId().equals(request.fromAccountId()) ? second : first;

        boolean validFundingSource = fundingSourceAllowed && from.isSystemAccount() && !to.isSystemAccount();
        if ((from.isSystemAccount() || to.isSystemAccount()) && !validFundingSource) {
            throw new SystemAccountRestrictedException();
        }
        if (from.getCurrency() != to.getCurrency()) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        BigDecimal amount = Money.normalize(request.amount());
        from.debit(amount);
        to.credit(amount);

        Transfer transfer = transfers.saveAndFlush(
                new Transfer(idempotencyKey, from, to, amount, from.getCurrency())
        );
        ledgerEntries.saveAll(List.of(
                new LedgerEntry(transfer, from, LedgerDirection.DEBIT, amount),
                new LedgerEntry(transfer, to, LedgerDirection.CREDIT, amount)
        ));
        return transfer;
    }
}
