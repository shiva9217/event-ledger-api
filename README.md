# Event Ledger API

A production-quality Spring Boot REST API for ingesting and querying financial ledger events with idempotent POST semantics and chronological balance computation.

---

## Prerequisites

- Java 17+
- Maven 3.8+

---

## Build

```bash
mvn clean install
```

---

## Run

```bash
mvn spring-boot:run
```

Server starts on **http://localhost:8080**

---

## Test

```bash
mvn test
```

All 17 integration tests must pass.

---

## H2 Console

Available at **http://localhost:8080/h2-console** while the app is running.

| Setting | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:ledgerdb` |
| Username | `sa` |
| Password | _(blank)_ |

---

## Swagger UI

Available at **http://localhost:8080/swagger-ui.html**

---

## API Endpoints & curl Examples

### POST /events — Ingest a new event (idempotent)

```bash
curl -s -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
  }'
```

**201 Created** (new event) or **200 OK** (duplicate — returns original, no side-effects).

---

### GET /events/{id} — Fetch a single event

```bash
curl -s http://localhost:8080/events/evt-001
```

**200 OK** or **404 Not Found**.

---

### GET /events?account={accountId} — List events for an account

Returns events ordered by `eventTimestamp` ASC regardless of arrival order.

```bash
curl -s "http://localhost:8080/events?account=acct-123"
```

---

### GET /accounts/{accountId}/balance — Compute net balance

```bash
curl -s http://localhost:8080/accounts/acct-123/balance
```

Response:

```json
{
  "accountId": "acct-123",
  "balance": 150.00,
  "currency": "USD",
  "eventCount": 1
}
```

Balance = SUM(CREDIT) − SUM(DEBIT). Returns `0.00` if no events exist for the account.

---

## Error Response Shape

All errors return a consistent JSON body:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "amount must be greater than 0",
  "timestamp": "2026-05-15T14:05:00Z"
}
```

| `error` code | HTTP status | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Missing/invalid field, bad type, zero/negative amount |
| `NOT_FOUND` | 404 | Event does not exist |
| `INTERNAL_ERROR` | 500 | Unexpected server fault |

---

## Branch Structure

```
main
 └── develop
      ├── feature/initial-setup
      ├── feature/event-entity-repository
      ├── feature/request-validation-dtos
      ├── feature/post-events-idempotency
      ├── feature/get-events-endpoints
      ├── feature/balance-endpoint
      ├── feature/exception-handler
      ├── feature/integration-tests
      └── feature/readme
```

Flow: `feature/*` → `develop` → `main`

---

## Domain Model

| Field | Type | Notes |
|---|---|---|
| `eventId` | String (PK) | Natural key from upstream; unique constraint enforces idempotency |
| `accountId` | String (indexed) | Owning account |
| `type` | Enum | `CREDIT` or `DEBIT` |
| `amount` | BigDecimal | Must be > 0 |
| `currency` | String | e.g. `USD` |
| `eventTimestamp` | Instant | When the event **occurred** |
| `receivedAt` | Instant | When the API **received** it |
| `metadata` | String | Optional JSON string |
