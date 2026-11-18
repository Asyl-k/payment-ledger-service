package dev.asyl.portfolio.paymentledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.asyl.portfolio.paymentledger.account.AccountRepository;
import dev.asyl.portfolio.paymentledger.ledger.LedgerEntryRepository;
import dev.asyl.portfolio.paymentledger.transfer.TransferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AccountRepository accounts;

    @Autowired
    protected TransferRepository transfers;

    @Autowired
    protected LedgerEntryRepository ledgerEntries;

    protected JsonNode createAccount(String ownerName, String currency) {
        ResponseEntity<String> response = post("/api/accounts", null, Map.of(
                "ownerName", ownerName,
                "currency", currency
        ));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        return json(response);
    }

    protected ResponseEntity<String> post(String path, String idempotencyKey, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> get(String path) {
        return rest.getForEntity(path, String.class);
    }

    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            throw new IllegalStateException("Response is not valid JSON: " + response.getBody(), ex);
        }
    }

    protected BigDecimal balance(long accountId) {
        ResponseEntity<String> response = get("/api/accounts/" + accountId);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response).get("balance").decimalValue();
    }

    protected String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }

    protected String uniqueOwner(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
