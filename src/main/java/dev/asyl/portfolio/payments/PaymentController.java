package dev.asyl.portfolio.payments;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Transfer transfer(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferRequest request){
        return service.transfer(key,request.sourceAccount(),request.targetAccount(),request.amount());
    }
}
