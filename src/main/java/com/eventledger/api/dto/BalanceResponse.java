package com.eventledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "Net balance for an account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    @Schema(example = "acct-123")
    private String accountId;

    @Schema(description = "Net balance: SUM(CREDIT) - SUM(DEBIT), rounded to 2 decimal places", example = "300.00")
    private BigDecimal balance;

    @Schema(description = "Currency from the earliest event; defaults to USD when no events exist", example = "USD")
    private String currency;

    @Schema(description = "Total number of events recorded for this account", example = "2")
    private long eventCount;
}
