package com.eventledger.api.controller;

import com.eventledger.api.dto.BalanceResponse;
import com.eventledger.api.dto.ErrorResponse;
import com.eventledger.api.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Accounts", description = "Account-level balance queries")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final EventService eventService;

    @Operation(
        summary = "Get account balance",
        description = """
            Computes the net balance for an account: **SUM(CREDIT) - SUM(DEBIT)**.

            - Returns `balance: 0.00` and `eventCount: 0` when no events exist for the account.
            - `currency` is taken from the earliest event by `eventTimestamp`; defaults to `USD` \
            when no events exist.
            - Balance is not affected by duplicate event submissions (idempotent POST).
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Balance computed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = BalanceResponse.class),
                examples = {
                    @ExampleObject(name = "with events", value = """
                        {
                          "accountId": "acct-123",
                          "balance": 300.00,
                          "currency": "USD",
                          "eventCount": 2
                        }"""),
                    @ExampleObject(name = "no events", value = """
                        {
                          "accountId": "acct-unknown",
                          "balance": 0.00,
                          "currency": "USD",
                          "eventCount": 0
                        }""")
                }
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "error": "INTERNAL_ERROR",
                      "message": "An unexpected error occurred",
                      "timestamp": "2026-05-15T14:05:00Z"
                    }""")
            )
        )
    })
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account identifier", example = "acct-123")
            @PathVariable String accountId) {
        return ResponseEntity.ok(eventService.getBalance(accountId));
    }
}
