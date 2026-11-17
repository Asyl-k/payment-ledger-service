package dev.asyl.portfolio.payments;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record TransferRequest(@NotBlank String sourceAccount,@NotBlank String targetAccount,@NotNull @DecimalMin(value="0.01") BigDecimal amount){}
