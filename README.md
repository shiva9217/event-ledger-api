# Event Ledger API

A production-quality Spring Boot REST API for ingesting and querying financial ledger events with idempotent POST semantics, paginated queries, thread-safe concurrency handling, and chronological balance computation.

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (optional — for containerised run)

---

## Branch Structure

```
main
 └── develop-v2
      ├── feature/pagination
      ├── feature/swagger-openapi
      ├── feature/concurrency-handling
      └── feature/docker-setup
```

Flow: `feature/*` → `develop-v2` → `main`

---

## Running Options

### Option 1 — Local (Maven)

```bash
mvn clean install
mvn spring-boot:run
```

Server starts on **http://localhost:8080**

### Option 2 — Docker Compose

```bash
docker compose up --build
```

Server starts on **http://localhost:8080**  
Health status visible in Docker Desktop or via:

```bash
docker inspect event-ledger-api --format='{{.State.Health.Status}}'
```

---

## Test

```bash
mvn test
```

All 31 integration tests must pass.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|---|---|
| `POST` | `/events` | Submit event (idempotent) |
| `GET` | `/events/{id}` | Get event by ID |
| `GET` | `/events?account={id}&page=0&size=20` | List events paginated, ordered by eventTimestamp ASC |
| `GET` | `/accounts/{accountId}/balance` | Get net balance (CREDIT − DEBIT) |
| `GET` | `/swagger-ui.html` | Swagger UI |
| `GET` | `/api-docs` | OpenAPI JSON |
| `GET` | `/actuator/health` | Health check (used by Docker healthcheck) |

---

## curl Examples

### POST /events — Submit a new event (idempotent)

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

### GET /events/{id} — Fetch a single event

```bash
curl -s http://localhost:8080/events/evt-001
```

**200 OK** or **404 Not Found**.

### GET /events?account= — List events (paginated)

```bash
curl -s "http://localhost:8080/events?account=acct-123&page=0&size=20"
```

Returns a pagination envelope:

```json
{
  "content": [ ...events... ],
  "page": 0,
  "size": 20,
  "totalElements": 53,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

Events are always ordered by `eventTimestamp` ASC regardless of arrival order.

### GET /accounts/{accountId}/balance — Compute net balance

```bash
curl -s http://localhost:8080/accounts/acct-123/balance
```

```json
{
  "accountId": "acct-123",
  "balance": 150.00,
  "currency": "USD",
  "eventCount": 1
}
```

Balance = SUM(CREDIT) − SUM(DEBIT). Returns `0.00` if no events exist.

---

## Concurrency

Two-layer idempotency protection for simultaneous `POST /events` with the same `eventId`:

| Layer | Mechanism | Purpose |
|-------|---|---|
| 1 | `ReentrantLock` per `eventId` in a `ConcurrentHashMap` | Serialises concurrent threads at application level; DB commit occurs **inside** the lock via `TransactionTemplate` |
| 2 | DB primary key (`eventId = @Id`) + catch `DataIntegrityViolationException` | Ultimate safety net — handles any race that slips past Layer 1 |

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
| `VALIDATION_ERROR` | 400 | Missing/invalid field, bad type, zero/negative amount, invalid page params |
| `NOT_FOUND` | 404 | Event does not exist |
| `INTERNAL_ERROR` | 500 | Unexpected server fault |

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
OpenAPI JSON at **http://localhost:8080/api-docs**

---

## Domain Model

| Field | Type | Notes |
|---|---|---|
| `eventId` | String (PK) | Natural key from upstream; unique constraint enforces idempotency |
| `accountId` | String (indexed) | Owning account |
| `type` | Enum | `CREDIT` or `DEBIT` |
| `amount` | BigDecimal | Must be > 0; stored at 4dp, returned at 2dp |
| `currency` | String | e.g. `USD` |
| `eventTimestamp` | Instant | When the event **occurred** |
| `receivedAt` | Instant | When the API **received** it |
| `metadata` | String (JSON) | Optional — accepts any JSON value (object, array, string, number, null) |
