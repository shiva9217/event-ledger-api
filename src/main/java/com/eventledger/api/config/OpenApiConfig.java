package com.eventledger.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event Ledger API")
                        .version("1.0.0")
                        .description("""
                                A production-quality financial event ledger that records CREDIT and DEBIT \
                                events per account and computes net balances.

                                **Key behaviours:**
                                - POST /events is idempotent: submitting the same eventId twice returns \
                                the original event (200) without creating a duplicate or changing the balance.
                                - Events are always returned ordered by `eventTimestamp` (when the event \
                                occurred), never by arrival order.
                                - Balance = SUM(CREDIT amounts) - SUM(DEBIT amounts).
                                - Metadata accepts any valid JSON value (object, array, string, number, null).
                                """)
                        .contact(new Contact()
                                .name("Event Ledger Team"))
                        .license(new License()
                                .name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")));
    }
}
