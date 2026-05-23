package com.eventledger.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Request body for creating a ledger event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {

    @Schema(description = "Unique identifier for this event (used for idempotency)", example = "evt-001")
    @NotBlank(message = "eventId is required")
    private String eventId;

    @Schema(description = "Account this event belongs to", example = "acct-123")
    @NotBlank(message = "accountId is required")
    private String accountId;

    @Schema(description = "Event type: must be exactly CREDIT or DEBIT (case-sensitive)", example = "CREDIT",
            allowableValues = {"CREDIT", "DEBIT"})
    @NotBlank(message = "type is required")
    private String type;

    @Schema(description = "Monetary amount — must be greater than 0", example = "150.00")
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    @Schema(description = "ISO 4217 currency code", example = "USD")
    @NotBlank(message = "currency is required")
    private String currency;

    @Schema(description = "When the event occurred (ISO 8601 / UTC)", example = "2026-05-15T14:02:11Z")
    @NotNull(message = "eventTimestamp is required")
    private Instant eventTimestamp;

    @Schema(
        description = "Optional arbitrary JSON metadata — accepts any JSON value: object, array, string, number, or null",
        example = "{\"source\": \"mainframe-batch\", \"batchId\": \"B-9042\"}",
        nullable = true
    )
    private JsonNode metadata;
}
