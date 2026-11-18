package dev.asyl.portfolio.paymentledger;

import com.fasterxml.jackson.databind.JsonNode;
import dev.asyl.portfolio.paymentledger.account.Currency;
import dev.asyl.portfolio.paymentledger.ledger.LedgerDirection;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PaymentApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void accountLifecycleAndSystemAccountVisibility() {
        JsonNode account = createAccount("  Alice  ", "USD");
        long accountId = account.get("id").asLong();

        assertThat(account.get("ownerName").asText()).isEqualTo("Alice");
        assertThat(account.get("currency").asText()).isEqualTo("USD");
        assertThat(account.get("balance").decimalValue()).isEqualByComparingTo("0.00");

        ResponseEntity<String> fetched = get("/api/accounts/" + accountId);
        assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        assertThat(json(fetched).get("id").asLong()).isEqualTo(accountId);

        JsonNode page = json(get("/api/accounts?size=100"));
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        page.get("content").forEach(node ->
                assertThat(node.get("ownerName").asText()).isNotEqualTo("External funding"));

        assertProblem(get("/api/accounts/999999"), 404, "account_not_found");
        assertProblem(get("/api/accounts/1"), 404, "account_not_found");
        assertProblem(get("/api/accounts/1/ledger"), 404, "account_not_found");
    }

    @Test
    void depositAndTransferProduceBalancedLedgerAndReplayIsIdempotent() {
        long alice = createAccount(uniqueOwner("alice"), "USD").get("id").asLong();
        long bob = createAccount(uniqueOwner("bob"), "USD").get("id").asLong();

        ResponseEntity<String> deposit = deposit(alice, "100.00", uniqueKey());
        assertThat(deposit.getStatusCode().value()).isEqualTo(201);
        JsonNode depositBody = json(deposit);
        assertThat(depositBody.get("fromAccountId").asLong()).isEqualTo(1L);
        assertThat(depositBody.get("toAccountId").asLong()).isEqualTo(alice);
        assertThat(depositBody.get("amount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(deposit.getHeaders().getLocation()).isNotNull();

        String key = uniqueKey();
        ResponseEntity<String> first = transfer(alice, bob, "25.00", key);
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        long transferId = json(first).get("id").asLong();

        ResponseEntity<String> replay = transfer(alice, bob, "25.00", key);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(json(replay).get("id").asLong()).isEqualTo(transferId);

        assertThat(balance(alice)).isEqualByComparingTo("75.00");
        assertThat(balance(bob)).isEqualByComparingTo("25.00");

        JsonNode aliceLedger = json(get("/api/accounts/" + alice + "/ledger?size=100"));
        assertThat(aliceLedger.get("totalElements").asLong()).isEqualTo(2);
        assertLedgerContains(aliceLedger, LedgerDirection.CREDIT, "100.00");
        assertLedgerContains(aliceLedger, LedgerDirection.DEBIT, "25.00");

        JsonNode bobLedger = json(get("/api/accounts/" + bob + "/ledger?size=100"));
        assertThat(bobLedger.get("totalElements").asLong()).isEqualTo(1);
        assertLedgerContains(bobLedger, LedgerDirection.CREDIT, "25.00");

        assertThat(ledgerEntries.sumByAccountAndDirection(alice, LedgerDirection.CREDIT))
                .isEqualByComparingTo("100.00");
        assertThat(ledgerEntries.sumByAccountAndDirection(alice, LedgerDirection.DEBIT))
                .isEqualByComparingTo("25.00");
        assertThat(ledgerEntries.sumByAccountAndDirection(bob, LedgerDirection.CREDIT))
                .isEqualByComparingTo("25.00");

        JsonNode transferPage = json(get("/api/accounts/" + alice + "/transfers?size=100"));
        assertThat(transferPage.get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void invalidRequestsReturnStableProblemCodes() {
        long alice = createAccount(uniqueOwner("alice"), "USD").get("id").asLong();
        long bob = createAccount(uniqueOwner("bob"), "USD").get("id").asLong();
        long eur = createAccount(uniqueOwner("euro"), "EUR").get("id").asLong();

        assertProblem(transfer(alice, bob, "10.00", uniqueKey()), 409, "insufficient_funds");
        assertProblem(transfer(alice, alice, "10.00", uniqueKey()), 400, "same_account");
        assertProblem(transfer(999999, bob, "10.00", uniqueKey()), 404, "account_not_found");
        assertProblem(transfer(alice, eur, "10.00", uniqueKey()), 400, "currency_mismatch");
        assertProblem(post("/api/transfers", null, Map.of(
                "fromAccountId", alice,
                "toAccountId", bob,
                "amount", new BigDecimal("10.00")
        )), 400, "idempotency_key_required");
        assertProblem(transfer(alice, bob, "10.00", "short"), 400, "idempotency_key_invalid");

        assertThat(deposit(alice, "100.00", uniqueKey()).getStatusCode().value()).isEqualTo(201);

        String key = uniqueKey();
        assertThat(transfer(alice, bob, "10.00", key).getStatusCode().value()).isEqualTo(201);
        assertProblem(transfer(alice, bob, "11.00", key), 409, "idempotency_key_conflict");

        assertThat(balance(alice)).isEqualByComparingTo("90.00");
        assertThat(balance(bob)).isEqualByComparingTo("10.00");
    }

    @Test
    void concurrentTransfersCannotOverdraw() throws Exception {
        long alice = createAccount(uniqueOwner("alice"), "USD").get("id").asLong();
        long bob = createAccount(uniqueOwner("bob"), "USD").get("id").asLong();
        assertThat(deposit(alice, "100.00", uniqueKey()).getStatusCode().value()).isEqualTo(201);

        List<Integer> statuses = runConcurrently(12, () ->
                transfer(alice, bob, "10.00", uniqueKey()).getStatusCode().value());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(10);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(2);

        assertThat(balance(alice)).isEqualByComparingTo("0.00");
        assertThat(balance(bob)).isEqualByComparingTo("100.00");
        assertThat(ledgerEntries.sumByAccountAndDirection(alice, LedgerDirection.CREDIT))
                .isEqualByComparingTo("100.00");
        assertThat(ledgerEntries.sumByAccountAndDirection(alice, LedgerDirection.DEBIT))
                .isEqualByComparingTo("100.00");
        assertThat(ledgerEntries.sumByAccountAndDirection(bob, LedgerDirection.CREDIT))
                .isEqualByComparingTo("100.00");

        JsonNode aliceLedger = json(get("/api/accounts/" + alice + "/ledger?size=100"));
        assertThat(aliceLedger.get("totalElements").asLong()).isEqualTo(11);
        JsonNode aliceTransfers = json(get("/api/accounts/" + alice + "/transfers?size=100"));
        assertThat(aliceTransfers.get("totalElements").asLong()).isEqualTo(11);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOneTransfer() throws Exception {
        long alice = createAccount(uniqueOwner("alice"), "USD").get("id").asLong();
        long bob = createAccount(uniqueOwner("bob"), "USD").get("id").asLong();
        assertThat(deposit(alice, "100.00", uniqueKey()).getStatusCode().value()).isEqualTo(201);

        String sharedKey = uniqueKey();
        List<Attempt> attempts = runConcurrently(8, () -> {
            ResponseEntity<String> response = transfer(alice, bob, "10.00", sharedKey);
            return new Attempt(response.getStatusCode().value(), json(response).get("id").asLong());
        });

        Set<Long> ids = attempts.stream()
                .map(Attempt::id)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(ids).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.status() == 201).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.status() == 200).hasSize(7);

        assertThat(balance(alice)).isEqualByComparingTo("90.00");
        assertThat(balance(bob)).isEqualByComparingTo("10.00");
        JsonNode aliceLedger = json(get("/api/accounts/" + alice + "/ledger?size=100"));
        assertThat(aliceLedger.get("totalElements").asLong()).isEqualTo(2);
    }

    private void assertLedgerContains(JsonNode page, LedgerDirection direction, String amount) {
        boolean found = false;
        for (JsonNode entry : page.get("content")) {
            if (entry.get("direction").asText().equals(direction.name())
                    && entry.get("amount").decimalValue().compareTo(new BigDecimal(amount)) == 0) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .as("ledger entry %s %s", direction, amount)
                .isTrue();
    }

    private void assertProblem(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(json(response).get("code").asText()).isEqualTo(code);
        assertThat(json(response).get("timestamp").asText()).isNotBlank();
    }

    private ResponseEntity<String> deposit(long accountId, String amount, String key) {
        return post("/api/accounts/" + accountId + "/deposits", key,
                Map.of("amount", new BigDecimal(amount)));
    }

    private ResponseEntity<String> transfer(long from, long to, String amount, String key) {
        return post("/api/transfers", key, Map.of(
                "fromAccountId", from,
                "toAccountId", to,
                "amount", new BigDecimal(amount)
        ));
    }

    private <T> List<T> runConcurrently(int tasks, ConcurrentTask<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<T>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return action.run();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }, pool));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (CompletableFuture<T> future : futures) {
                results.add(future.join());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConcurrentTask<T> {
        T run() throws Exception;
    }

    private record Attempt(int status, long id) {
    }
}
