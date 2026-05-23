package com.eventledger.api;

import com.eventledger.api.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventLedgerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    // ─── 1. POST valid CREDIT → 201 ──────────────────────────────────────────

    @Test
    void postValidCreditEvent_returns201() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-001",
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": 150.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    // ─── 2. POST valid DEBIT → 201 ───────────────────────────────────────────

    @Test
    void postValidDebitEvent_returns201() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-002",
                                  "accountId": "acct-001",
                                  "type": "DEBIT",
                                  "amount": 50.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:05:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEBIT"));
    }

    // ─── 3. POST duplicate eventId → 200 with original, no duplicate stored ──

    @Test
    void postDuplicateEventId_returns200WithOriginal() throws Exception {
        String body = """
                {
                  "eventId": "evt-dup",
                  "accountId": "acct-001",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-dup"));

        // assertThat (JUnit/AssertJ) is always active — unlike bare Java `assert`
        assertThat(eventRepository.count())
                .as("duplicate submission must not insert a second row")
                .isEqualTo(1);
    }

    // ─── 4. POST missing required field (eventId) → 400 ─────────────────────

    @Test
    void postMissingEventId_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("eventId")));
    }

    // ─── 5. POST missing accountId → 400 ────────────────────────────────────

    @Test
    void postMissingAccountId_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-no-acct",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("accountId")));
    }

    // ─── 6. POST amount = 0 → 400 ────────────────────────────────────────────

    @Test
    void postAmountZero_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-z",
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": 0,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("amount")));
    }

    // ─── 7. POST amount = -50 → 400 ──────────────────────────────────────────

    @Test
    void postNegativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-neg",
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": -50,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("amount")));
    }

    // ─── 8. POST invalid type (not CREDIT/DEBIT) → 400 ───────────────────────

    @Test
    void postInvalidType_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-bad-type",
                                  "accountId": "acct-001",
                                  "type": "TRANSFER",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("CREDIT")));
    }

    // ─── 9. POST missing type field → 400 ────────────────────────────────────

    @Test
    void postMissingType_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-no-type",
                                  "accountId": "acct-001",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("type")));
    }

    // ─── 10. POST invalid eventTimestamp format → 400 ────────────────────────

    @Test
    void postInvalidTimestampFormat_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-bad-ts",
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "not-a-timestamp"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ─── 11. GET /events/{id} existing → 200 ─────────────────────────────────

    @Test
    void getExistingEvent_returns200() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-get",
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": 75.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events/evt-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-get"))
                .andExpect(jsonPath("$.amount").value(75.00));
    }

    // ─── 12. GET /events/{id} missing → 404 ──────────────────────────────────

    @Test
    void getMissingEvent_returns404() throws Exception {
        mockMvc.perform(get("/events/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ─── 13. GET /events?account= → ASC eventTimestamp (out-of-order arrival) ─

    @Test
    void getEventsByAccount_returnsChronologicalOrder() throws Exception {
        // Post later timestamp first to prove ordering is by eventTimestamp, not arrival
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-late",
                                  "accountId": "acct-order",
                                  "type": "CREDIT",
                                  "amount": 200.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T15:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-early",
                                  "accountId": "acct-order",
                                  "type": "DEBIT",
                                  "amount": 50.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T09:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events").param("account", "acct-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].eventId").value("evt-early"))
                .andExpect(jsonPath("$.content[1].eventId").value("evt-late"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // ─── 14. GET /accounts/{accountId}/balance → correct net balance ──────────

    @Test
    void getBalance_returnsCorrectNetBalance() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId": "bal-c1",
                          "accountId": "acct-bal",
                          "type": "CREDIT",
                          "amount": 500.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T10:00:00Z"
                        }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId": "bal-d1",
                          "accountId": "acct-bal",
                          "type": "DEBIT",
                          "amount": 150.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T11:00:00Z"
                        }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/acct-bal/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-bal"))
                .andExpect(jsonPath("$.balance").value(350.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.eventCount").value(2));
    }

    // ─── 15. GET balance → correct even when events arrive out of order ────────

    @Test
    void getBalance_correctWhenEventsArriveOutOfOrder() throws Exception {
        // Send a later-occurring DEBIT first, earlier-occurring CREDIT second
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId": "ooo-d",
                          "accountId": "acct-ooo",
                          "type": "DEBIT",
                          "amount": 200.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T12:00:00Z"
                        }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId": "ooo-c",
                          "accountId": "acct-ooo",
                          "type": "CREDIT",
                          "amount": 700.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T08:00:00Z"
                        }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/acct-ooo/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.eventCount").value(2));
    }

    // ─── 16. GET balance for account with no events → 0.00 ───────────────────

    @Test
    void getBalanceNoEvents_returnsZero() throws Exception {
        mockMvc.perform(get("/accounts/acct-empty/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-empty"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.eventCount").value(0));
    }

    // ─── 17. Duplicate submission does NOT change balance ─────────────────────

    @Test
    void duplicateEvent_doesNotAffectBalance() throws Exception {
        String body = """
                {
                  "eventId": "evt-idem",
                  "accountId": "acct-idem",
                  "type": "CREDIT",
                  "amount": 300.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/accounts/acct-idem/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00))
                .andExpect(jsonPath("$.eventCount").value(1));

        assertThat(eventRepository.count())
                .as("only one row must exist after duplicate submission")
                .isEqualTo(1);
    }

    // ─── 18. POST with metadata as JSON object → stored and returned correctly ─

    @Test
    void postEventWithJsonObjectMetadata_roundTripsCorrectly() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-meta",
                                  "accountId": "acct-001",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z",
                                  "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.source").value("mainframe-batch"))
                .andExpect(jsonPath("$.metadata.batchId").value("B-9042"));
    }

    // ─── Pagination tests ─────────────────────────────────────────────────────

    private void postEvent(String id, String accountId, String type, double amount, String ts) throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {"eventId":"%s","accountId":"%s","type":"%s","amount":%.2f,
                         "currency":"USD","eventTimestamp":"%s"}""", id, accountId, type, amount, ts)))
                .andExpect(status().isCreated());
    }

    @Test
    void pagination_defaultPageAndSize() throws Exception {
        postEvent("p-evt-1", "acct-page", "CREDIT", 100.0, "2026-05-01T10:00:00Z");
        postEvent("p-evt-2", "acct-page", "DEBIT",  50.0, "2026-05-02T10:00:00Z");

        mockMvc.perform(get("/events").param("account", "acct-page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void pagination_customPageAndSize() throws Exception {
        for (int i = 1; i <= 5; i++) {
            postEvent("pg-evt-" + i, "acct-custom", "CREDIT", 10.0 * i,
                    "2026-05-0" + i + "T10:00:00Z");
        }

        mockMvc.perform(get("/events")
                        .param("account", "acct-custom")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false));
    }

    @Test
    void pagination_sizeGreaterThan100_returns400() throws Exception {
        mockMvc.perform(get("/events")
                        .param("account", "acct-x")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("size")));
    }

    @Test
    void pagination_negativePageNumber_returns400() throws Exception {
        mockMvc.perform(get("/events")
                        .param("account", "acct-x")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void pagination_correctTotalElementsAndTotalPages() throws Exception {
        for (int i = 1; i <= 7; i++) {
            postEvent("tot-evt-" + i, "acct-total", "CREDIT", 10.0,
                    "2026-05-0" + i + "T10:00:00Z");
        }

        mockMvc.perform(get("/events")
                        .param("account", "acct-total")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    void pagination_eventTimestampAscOrderWithinPage() throws Exception {
        // Arrive out of order — third, first, second
        postEvent("ord-evt-3", "acct-ord", "CREDIT", 30.0, "2026-05-03T10:00:00Z");
        postEvent("ord-evt-1", "acct-ord", "CREDIT", 10.0, "2026-05-01T10:00:00Z");
        postEvent("ord-evt-2", "acct-ord", "CREDIT", 20.0, "2026-05-02T10:00:00Z");

        mockMvc.perform(get("/events")
                        .param("account", "acct-ord")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("ord-evt-1"))
                .andExpect(jsonPath("$.content[1].eventId").value("ord-evt-2"))
                .andExpect(jsonPath("$.content[2].eventId").value("ord-evt-3"));
    }

    // ─── 19. POST without metadata → accepted, metadata is null in response ───

    @Test
    void postEventWithoutMetadata_isAccepted() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-no-meta",
                                  "accountId": "acct-001",
                                  "type": "DEBIT",
                                  "amount": 25.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-no-meta"))
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }
}
