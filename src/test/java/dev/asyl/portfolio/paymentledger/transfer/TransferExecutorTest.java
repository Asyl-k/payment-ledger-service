package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.account.Account;
import dev.asyl.portfolio.paymentledger.account.AccountRepository;
import dev.asyl.portfolio.paymentledger.account.Currency;
import dev.asyl.portfolio.paymentledger.ledger.LedgerDirection;
import dev.asyl.portfolio.paymentledger.ledger.LedgerEntry;
import dev.asyl.portfolio.paymentledger.ledger.LedgerEntryRepository;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.CreateTransferRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferExecutorTest {

    @Mock
    AccountRepository accounts;

    @Mock
    TransferRepository transfers;

    @Mock
    LedgerEntryRepository ledgerEntries;

    @InjectMocks
    TransferExecutor executor;

    @Test
    @SuppressWarnings("unchecked")
    void locksAccountsInAscendingOrderAndWritesBothLedgerSides() {
        Account lowId = account(1L, Currency.USD, "0.00", false);
        Account highId = account(2L, Currency.USD, "100.00", false);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(lowId));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(highId));
        when(transfers.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer transfer = invocation.getArgument(0);
            ReflectionTestUtils.setField(transfer, "id", 100L);
            return transfer;
        });

        Transfer transfer = executor.execute(
                "transfer-key-0001",
                new CreateTransferRequest(2L, 1L, new BigDecimal("40.00")),
                false
        );

        InOrder order = inOrder(accounts);
        order.verify(accounts).findByIdForUpdate(1L);
        order.verify(accounts).findByIdForUpdate(2L);

        assertThat(highId.getBalance()).isEqualByComparingTo("60.00");
        assertThat(lowId.getBalance()).isEqualByComparingTo("40.00");
        assertThat(transfer.getFromAccount()).isSameAs(highId);
        assertThat(transfer.getToAccount()).isSameAs(lowId);
        assertThat(transfer.getCurrency()).isEqualTo(Currency.USD);

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntries).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(LedgerEntry::getDirection)
                .containsExactly(LedgerDirection.DEBIT, LedgerDirection.CREDIT);
        assertThat(captor.getValue())
                .extracting(LedgerEntry::getAmount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("40.00"));
    }

    @Test
    void insufficientFundsAbortsWithoutPersisting() {
        Account source = account(1L, Currency.USD, "10.00", false);
        Account target = account(2L, Currency.USD, "0.00", false);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> executor.execute(
                "transfer-key-0001",
                new CreateTransferRequest(1L, 2L, new BigDecimal("25.00")),
                false
        )).isInstanceOf(InsufficientFundsException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("10.00");
        assertThat(target.getBalance()).isEqualByComparingTo("0.00");
        verify(transfers, never()).saveAndFlush(any(Transfer.class));
        verify(ledgerEntries, never()).saveAll(any());
    }

    @Test
    void currencyMismatchIsRejected() {
        Account source = account(1L, Currency.USD, "100.00", false);
        Account target = account(2L, Currency.EUR, "0.00", false);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> executor.execute(
                "transfer-key-0001",
                new CreateTransferRequest(1L, 2L, new BigDecimal("10.00")),
                false
        )).isInstanceOf(CurrencyMismatchException.class);

        verify(transfers, never()).saveAndFlush(any(Transfer.class));
    }

    @Test
    void fundingAccountCannotBeUsedByARegularTransfer() {
        Account funding = account(1L, Currency.USD, "0.00", true);
        Account target = account(2L, Currency.USD, "0.00", false);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(funding));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> executor.execute(
                "transfer-key-0001",
                new CreateTransferRequest(1L, 2L, new BigDecimal("10.00")),
                false
        )).isInstanceOf(SystemAccountRestrictedException.class);

        verify(transfers, never()).saveAndFlush(any(Transfer.class));
    }

    @Test
    void depositMayDrawFromFundingAccount() {
        Account funding = account(1L, Currency.USD, "0.00", true);
        Account target = account(2L, Currency.USD, "0.00", false);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(funding));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(transfers.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer transfer = invocation.getArgument(0);
            ReflectionTestUtils.setField(transfer, "id", 100L);
            return transfer;
        });

        executor.execute(
                "deposit-key-0001",
                new CreateTransferRequest(1L, 2L, new BigDecimal("50.00")),
                true
        );

        assertThat(funding.getBalance()).isEqualByComparingTo("-50.00");
        assertThat(target.getBalance()).isEqualByComparingTo("50.00");
    }

    private Account account(Long id, Currency currency, String balance, boolean systemAccount) {
        Account account = new Account("Owner " + id, currency);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "systemAccount", systemAccount);
        return account;
    }
}
