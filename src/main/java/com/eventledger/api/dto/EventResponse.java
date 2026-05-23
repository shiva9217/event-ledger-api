package com.eventledger.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Ledger event response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {

    @Schema(example = "evt-001")
    private String eventId;

    @Schema(example = "acct-123")
    private String accountId;

    @Schema(example = "CREDIT", allowableValues = {"CREDIT", "DEBIT"})
    private String type;

    @Schema(example = "150.00")
    private BigDecimal amount;

    @Schema(example = "USD")
    private String currency;

    @Schema(description = "When the event occurred (ISO 8601 / UTC)", example = "2026-05-15T14:02:11Z")
    private Instant eventTimestamp;

    @Schema(description = "When the API received the event (ISO 8601 / UTC)", example = "2026-05-15T14:05:00Z")
    private Instant receivedAt;

    @Schema(
        description = "Arbitrary JSON metadata as supplied in the request — returned verbatim. May be any JSON value or null.",
        example = "{\"source\": \"mainframe-batch\", \"batchId\": \"B-9042\"}",
        nullable = true,
        type = "object"
    )
    @JsonRawValue
    private String metadata;
}
