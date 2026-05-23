package com.eventledger.api.controller;

import com.eventledger.api.dto.CreateEventResult;
import com.eventledger.api.dto.ErrorResponse;
import com.eventledger.api.dto.EventRequest;
import com.eventledger.api.dto.EventResponse;
import com.eventledger.api.dto.PagedResponse;
import com.eventledger.api.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Events", description = "Create and query financial ledger events")
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(
        summary = "Submit a ledger event",
        description = """
            Records a new CREDIT or DEBIT event against an account.

            **Idempotency:** if `eventId` already exists the original event is returned with HTTP 200 \
            and no duplicate is stored. A genuinely new event returns HTTP 201.

            **Metadata:** the `metadata` field accepts any valid JSON value — object, array, string, \
            number, or null. It is stored as-is and returned verbatim.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Event created",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EventResponse.class),
                examples = @ExampleObject(name = "credit event", value = """
                    {
                      "eventId": "evt-001",
                      "accountId": "acct-123",
                      "type": "CREDIT",
                      "amount": 150.00,
                      "currency": "USD",
                      "eventTimestamp": "2026-05-15T14:02:11Z",
                      "receivedAt": "2026-05-15T14:05:00Z",
                      "metadata": {"source": "mainframe-batch", "batchId": "B-9042"}
                    }""")
            )
        ),
        @ApiResponse(
            responseCode = "200",
            description = "Duplicate eventId — original event returned (no balance change)",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EventResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error — missing field, invalid type, or amount <= 0",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "missing field", value = """
                        {
                          "error": "VALIDATION_ERROR",
                          "message": "accountId is required",
                          "timestamp": "2026-05-15T14:05:00Z"
                        }"""),
                    @ExampleObject(name = "invalid type", value = """
                        {
                          "error": "VALIDATION_ERROR",
                          "message": "Invalid event type: 'TRANSFER'. Must be CREDIT or DEBIT",
                          "timestamp": "2026-05-15T14:05:00Z"
                        }"""),
                    @ExampleObject(name = "amount not positive", value = """
                        {
                          "error": "VALIDATION_ERROR",
                          "message": "amount must be greater than 0",
                          "timestamp": "2026-05-15T14:05:00Z"
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
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        CreateEventResult result = eventService.createEvent(request);
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.event());
    }

    @Operation(
        summary = "Get event by ID",
        description = "Returns a single event. Responds with 404 if the eventId does not exist."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Event found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EventResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "eventId": "evt-001",
                      "accountId": "acct-123",
                      "type": "CREDIT",
                      "amount": 150.00,
                      "currency": "USD",
                      "eventTimestamp": "2026-05-15T14:02:11Z",
                      "receivedAt": "2026-05-15T14:05:00Z",
                      "metadata": {"source": "mainframe-batch", "batchId": "B-9042"}
                    }""")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Event not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "error": "NOT_FOUND",
                      "message": "Event not found: evt-999",
                      "timestamp": "2026-05-15T14:05:00Z"
                    }""")
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
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(
            @Parameter(description = "Unique event identifier", example = "evt-001")
            @PathVariable String id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @Operation(
        summary = "List events for an account",
        description = "Returns all events for the given account ordered by `eventTimestamp` ascending (chronological order, regardless of arrival order)."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Event list (may be empty)",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EventResponse.class),
                examples = @ExampleObject(value = """
                    [
                      {
                        "eventId": "evt-001",
                        "accountId": "acct-123",
                        "type": "CREDIT",
                        "amount": 500.00,
                        "currency": "USD",
                        "eventTimestamp": "2026-05-10T08:00:00Z",
                        "receivedAt": "2026-05-10T08:01:00Z",
                        "metadata": null
                      },
                      {
                        "eventId": "evt-002",
                        "accountId": "acct-123",
                        "type": "DEBIT",
                        "amount": 200.00,
                        "currency": "USD",
                        "eventTimestamp": "2026-05-12T09:30:00Z",
                        "receivedAt": "2026-05-12T09:31:00Z",
                        "metadata": {"ref": "INV-7291"}
                      }
                    ]""")
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
    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByAccount(
            @Parameter(description = "Account identifier to filter events", example = "acct-123", required = true)
            @RequestParam("account") String accountId) {
        return ResponseEntity.ok(eventService.getEventsByAccount(accountId));
    }
}
