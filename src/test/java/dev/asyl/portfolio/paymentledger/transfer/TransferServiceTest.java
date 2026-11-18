package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.account.Account;
import dev.asyl.portfolio.paymentledger.account.AccountRepository;
import dev.asyl.portfolio.paymentledger.account.Currency;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.CreateTransferRequest;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.DepositRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String KEY = "transfer-key-0001";

    @Mock
    AccountRepository accounts;

    @Mock
    TransferRepository transfers;

    @Mock
    TransferExecutor executor;

    @InjectMocks
    TransferService service;

    @Test
    void replayReturnsExistingTransferWithoutMovingMoneyAgain() {
        Account from = account(10L, Currency.USD);
        Account to = account(20L, Currency.USD);
        Transfer existing = transfer(KEY, from, to, "25.00");
        when(transfers.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        TransferResult result = service.transfer(KEY, new CreateTransferRequest(10L, 20L, new BigDecimal("25.0")));

        assertThat(result.created()).isFalse();
        assertThat(result.response().id()).isEqualTo(100L);
        assertThat(result.response().amount()).isEqualByComparingTo("25.00");
        verifyNoInteractions(executor);
    }

    @Test
    void replayWithDifferentPayloadIsRejected() {
        Account from = account(10L, Currency.USD);
        Account to = account(20L, Currency.USD);
        when(transfers.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(transfer(KEY, from, to, "25.00")));

        assertThatThrownBy(() -> service.transfer(
                KEY,
                new CreateTransferRequest(10L, 20L, new BigDecimal("30.00"))
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void uniqueConstraintRaceFallsBackToReplay() {
        Account from = account(10L, Currency.USD);
        Account to = account(20L, Currency.USD);
        Transfer existing = transfer(KEY, from, to, "25.00");
        when(transfers.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(executor.execute(eq(KEY), any(CreateTransferRequest.class), eq(false)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        TransferResult result = service.transfer(KEY, new CreateTransferRequest(10L, 20L, new BigDecimal("25.00")));

        assertThat(result.created()).isFalse();
        assertThat(result.response().id()).isEqualTo(100L);
    }

    @Test
    void newTransferIsReportedAsCreated() {
        Account from = account(10L, Currency.USD);
        Account to = account(20L, Currency.USD);
        Transfer created = transfer(KEY, from, to, "25.00");
        when(transfers.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(executor.execute(eq(KEY), any(CreateTransferRequest.class), eq(false))).thenReturn(created);

        TransferResult result = service.transfer(KEY, new CreateTransferRequest(10L, 20L, new BigDecimal("25.00")));

        assertThat(result.created()).isTrue();
        assertThat(result.response().id()).isEqualTo(100L);
    }

    @Test
    void amountComparisonIgnoresTrailingZeros() {
        Account from = account(10L, Currency.USD);
        Account to = account(20L, Currency.USD);
        when(transfers.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(transfer(KEY, from, to, "25.00")));

        TransferResult result = service.transfer(KEY, new CreateTransferRequest(10L, 20L, new BigDecimal("25.0000")));

        assertThat(result.response().amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void sameAccountIsRejectedBeforeExecution() {
        assertThatThrownBy(() -> service.transfer(
                KEY,
                new CreateTransferRequest(10L, 10L, new BigDecimal("25.00"))
        )).isInstanceOf(SameAccountException.class);
        verifyNoInteractions(executor);
    }

    @Test
    void missingAndMalformedKeysAreRejected() {
        assertThatThrownBy(() -> service.transfer(" ", new CreateTransferRequest(1L, 2L, BigDecimal.ONE)))
                .isInstanceOf(MissingIdempotencyKeyException.class);
        assertThatThrownBy(() -> service.transfer("short", new CreateTransferRequest(1L, 2L, BigDecimal.ONE)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> service.transfer("ключ-ключ-ключ",
                        new CreateTransferRequest(1L, 2L, BigDecimal.ONE)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        verifyNoInteractions(executor);
    }

    @Test
    void depositUsesFundingAccountOfTargetCurrency() {
        Account target = account(20L, Currency.EUR);
        Account funding = account(2L, Currency.EUR);
        Transfer created = transfer(KEY, funding, target, "50.00");
        when(accounts.findById(20L)).thenReturn(Optional.of(target));
        when(accounts.findBySystemAccountTrueAndCurrency(Currency.EUR)).thenReturn(Optional.of(funding));
        when(executor.execute(eq(KEY), any(CreateTransferRequest.class), eq(true))).thenReturn(created);

        TransferResult result = service.deposit(KEY, 20L, new DepositRequest(new BigDecimal("50.00")));

        assertThat(result.created()).isTrue();
        assertThat(result.response().fromAccountId()).isEqualTo(2L);
        assertThat(result.response().toAccountId()).isEqualTo(20L);
    }

    @Test
    void depositToUnknownAccountFails() {
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(KEY, 99L, new DepositRequest(new BigDecimal("50.00"))))
                .isInstanceOf(dev.asyl.portfolio.paymentledger.account.AccountNotFoundException.class);
        verify(executor, never()).execute(any(), any(), eq(true));
    }

    private Account account(Long id, Currency currency) {
        Account account = new Account("Owner " + id, currency);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private Transfer transfer(String key, Account from, Account to, String amount) {
        Transfer transfer = new Transfer(key, from, to, new BigDecimal(amount), from.getCurrency());
        ReflectionTestUtils.setField(transfer, "id", 100L);
        return transfer;
    }
}
