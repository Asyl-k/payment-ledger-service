package dev.asyl.portfolio.paymentledger.transfer;

import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.CreateTransferRequest;
import dev.asyl.portfolio.paymentledger.transfer.TransferDtos.DepositRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        return respond(service.transfer(idempotencyKey, request));
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<TransferResponse> deposit(
            @PathVariable Long accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request
    ) {
        return respond(service.deposit(idempotencyKey, accountId, request));
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/accounts/{accountId}/transfers")
    public Page<TransferResponse> list(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return service.listForAccount(accountId, pageable);
    }

    private ResponseEntity<TransferResponse> respond(TransferResult result) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/transfers/{id}")
                .buildAndExpand(result.response().id())
                .toUri();
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).location(location).body(result.response());
    }
}
