package com.eventledger.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
    info = @Info(
        title = "Event Ledger API",
        version = "1.0.0",
        description = "Financial transaction event ledger with idempotency and out-of-order tolerance",
        contact = @Contact(
            name = "Event Ledger Team",
            url = "https://github.com/shiva9217/event-ledger-api"
        )
    ),
    servers = @Server(url = "http://localhost:8080", description = "Local development")
)
@SpringBootApplication
public class EventLedgerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventLedgerApiApplication.class, args);
    }
}
