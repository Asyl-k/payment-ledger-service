package dev.asyl.portfolio.paymentledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Ledger API")
                        .version("1.0.0")
                        .description("Double-entry payments with idempotent transfers and PostgreSQL row locks"));
    }
}
