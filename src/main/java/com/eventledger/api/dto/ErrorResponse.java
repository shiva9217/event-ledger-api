package com.eventledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Schema(description = "Error response returned for 4xx and 5xx status codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    @Schema(description = "Machine-readable error code",
            example = "VALIDATION_ERROR",
            allowableValues = {"VALIDATION_ERROR", "NOT_FOUND", "INTERNAL_ERROR"})
    private String error;

    @Schema(description = "Human-readable explanation of the error", example = "amount must be greater than 0")
    private String message;

    @Schema(description = "UTC timestamp when the error occurred", example = "2026-05-15T14:05:00Z")
    private Instant timestamp;
}
