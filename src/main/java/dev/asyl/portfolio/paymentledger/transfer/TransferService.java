package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.account.Account;
import dev.asyl.portfolio.paymentledger.account.AccountNotFoundException;
import dev.asyl.portfolio.paymentledger.account.AccountRepository;
import dev.asyl.portfolio.paymentledger.common.Money;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.CreateTransferRequest;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.DepositRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TransferService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final TransferExecutor executor;

    public TransferService(AccountRepository accounts,
                           TransferRepository transfers,
                           TransferExecutor executor) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.executor = executor;
    }

    public TransferResult transfer(String idempotencyKey, CreateTransferRequest request) {
        validateKey(idempotencyKey);
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SameAccountException();
        }
        return create(idempotencyKey, request, false);
    }

    public TransferResult deposit(String idempotencyKey, Long accountId, DepositRequest request) {
        validateKey(idempotencyKey);
        Account target = accounts.findById(accountId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Account funding = accounts.findBySystemAccountTrueAndCurrency(target.getCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "No external funding account configured for " + target.getCurrency()));
        CreateTransferRequest transfer = new CreateTransferRequest(
                funding.getId(),
                target.getId(),
                request.amount()
        );
        return create(idempotencyKey, transfer, true);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long id) {
        return transfers.findById(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> listForAccount(Long accountId, Pageable pageable) {
        requireVisibleAccount(accountId);
        return transfers.findByFromAccountIdOrToAccountId(accountId, accountId, pageable)
                .map(TransferResponse::from);
    }

    private TransferResult create(String idempotencyKey,
                                  CreateTransferRequest request,
                                  boolean fundingSourceAllowed) {
        Optional<Transfer> existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }
        try {
            Transfer created = executor.execute(idempotencyKey, request, fundingSourceAllowed);
            return new TransferResult(TransferResponse.from(created), true);
        } catch (DataIntegrityViolationException ex) {
            // Another request with the same idempotency key won the race.
            Transfer raced = transfers.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            return replay(raced, request);
        }
    }

    private TransferResult replay(Transfer existing, CreateTransferRequest request) {
        boolean samePayload = existing.getFromAccount().getId().equals(request.fromAccountId())
                && existing.getToAccount().getId().equals(request.toAccountId())
                && existing.getAmount().compareTo(Money.normalize(request.amount())) == 0;
        if (!samePayload) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        return new TransferResult(TransferResponse.from(existing), false);
    }

    private Account requireVisibleAccount(Long accountId) {
        return accounts.findById(accountId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
