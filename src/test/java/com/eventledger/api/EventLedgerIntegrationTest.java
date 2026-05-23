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
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value("evt-early"))
                .andExpect(jsonPath("$[1].eventId").value("evt-late"));
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
}
