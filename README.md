# Payment Service

A Payment Service REST API built with Spring Boot and Spring Modulith.
The service processes payments with transactional integrity, handles concurrency to prevent double-spending, persists to PostgreSQL, and
sends asynchronous notifications via Kafka.

## Tech Stack

- Java 21
- Spring Boot 3.4
- Spring Modulith (event-driven architecture)
- PostgreSQL 18
- Apache Kafka
- Liquibase (database migrations)
- Spock Framework with Groovy 5 (testing)
- Testcontainers (integration testing)

## Quick Start

### Prerequisites

- JDK 21+
- Docker & Docker Compose

### Run Dependencies

```bash
docker-compose up -d
```

### Build & Run

```bash
./gradlew build
./gradlew bootRun
```

The service will be available at `http://localhost:8080`.

### Run with Test Data

Test data is automatically loaded when running with the `local` profile (default). This includes pre-configured accounts for testing and
stress testing.

## API Endpoints

| Method | Endpoint                        | Description                                       |
|--------|---------------------------------|---------------------------------------------------|
| POST   | `/api/v1/accounts`              | Create a new account                              |
| GET    | `/api/v1/accounts/{id}/balance` | Get account balance                               |
| POST   | `/api/v1/payments`              | Submit a new payment                              |
| GET    | `/api/v1/payments/{id}`         | Get payment by ID                                 |
| GET    | `/api/v1/payments`              | List payments (with filters, pagination, sorting) |
| GET    | `/actuator/health`              | Health check                                      |

### Create Account

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "balance": "1000.00",
    "currency": "EUR"
  }'
```

### Submit Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "100.00",
    "currency": "EUR"
  }'
```

### List Payments with Filters

```bash
# Filter by sender
curl "http://localhost:8080/api/v1/payments?senderAccountId=8686a341-25a0-43b4-bf3e-2ed5f554452b"

# Filter by status
curl "http://localhost:8080/api/v1/payments?status=COMPLETED"

# Pagination and sorting
curl "http://localhost:8080/api/v1/payments?page=0&size=10&sort=createdAt,desc"
```

### Error Codes

| Code                       | Numeric | Description                                     |
|----------------------------|---------|-------------------------------------------------|
| PAYMENT_NOT_FOUND          | 1001    | Payment not found                               |
| DUPLICATE_PAYMENT          | 1002    | Duplicate payment request                       |
| PAYMENT_PROCESSING_FAILED  | 1003    | Payment processing failed                       |
| ACCOUNT_NOT_FOUND          | 2001    | Account not found                               |
| SENDER_ACCOUNT_NOT_FOUND   | 2002    | Sender account not found                        |
| RECEIVER_ACCOUNT_NOT_FOUND | 2003    | Receiver account not found                      |
| INSUFFICIENT_BALANCE       | 2004    | Insufficient balance                            |
| SAME_ACCOUNT               | 2005    | Sender and receiver accounts cannot be the same |
| VALIDATION_ERROR           | 3001    | Validation error                                |
| INVALID_AMOUNT             | 3002    | Invalid amount                                  |
| INVALID_CURRENCY           | 3003    | Invalid currency                                |
| INTERNAL_ERROR             | 5001    | Internal server error                           |

## Testing

```bash
# Run all tests
./gradlew test

# Run with coverage report
./gradlew test jacocoTestReport

# Run stress test (requires running application)
./scripts/stress-test.sh
```

## Architecture

### Event-Driven with Spring Modulith

The service uses Spring Modulith for event-driven architecture with transactional outbox pattern:

1. Client submits payment with idempotency key
2. Payment saved as PENDING + `PaymentCreatedEvent` published (same transaction)
3. Returns `202 Accepted` immediately
4. `PaymentEventListener` receives event, delegates to `PaymentProcessor`
5. `PaymentProcessor` executes transfer in its own transaction
6. On completion, `PaymentCompletedEvent` is saved to `event_publication` table
7. Spring Modulith externalizes event to Kafka after commit
8. Event marked as complete in `event_publication` table

### Guaranteed Delivery

- Events are persisted in the same DB transaction as business data (transactional outbox)
- Spring Modulith externalizes events to Kafka after commit
- Incomplete events are resubmitted via scheduled job (`IncompleteEventResubmitter`)
- Consumers must be idempotent (at-least-once delivery guarantee)

### Concurrency Control

- Optimistic locking with `@Version` on Account entity
- Idempotency keys prevent duplicate payments
- Database `CHECK (balance >= 0)` constraint as final safety net

## Kafka Events

Payment completion events are published to `payment-notifications` topic:

```json
{
  "paymentId": "uuid",
  "senderAccountId": "uuid",
  "receiverAccountId": "uuid",
  "amount": "100.00",
  "currency": "EUR",
  "status": "COMPLETED",
  "errorCode": null,
  "numericErrorCode": null,
  "errorMessage": null,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Configuration

See `application.yml` for all configuration options. Key environment variables:

| Variable                  | Default        | Description       |
|---------------------------|----------------|-------------------|
| `DB_HOST`                 | localhost      | PostgreSQL host   |
| `DB_PORT`                 | 5432           | PostgreSQL port   |
| `DB_NAME`                 | payments       | Database name     |
| `DB_USER`                 | postgres       | Database user     |
| `DB_PASSWORD`             | postgres       | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | localhost:9092 | Kafka servers     |

## Project Structure

```
com.payment/
├── config/          # Spring configuration
├── controller/      # REST controllers
├── event/           # Domain events
├── exception/       # Exception handling
├── model/           # JPA entities
├── repository/      # Data access
└── service/         # Business logic
```

## Future Improvements

The following improvements could be implemented to make the service more production-ready:

### Reliability & Resilience

- **Dead Letter Queue (DLQ) handling**: Implement retry tracking with exponential backoff and move permanently failed events to a DLQ topic
  after N retries
- **Circuit breaker**: Add circuit breaker pattern (e.g., Resilience4j) for Kafka producer to fail fast when broker is unavailable
- **Distributed tracing**: Add correlation IDs and integrate with distributed tracing (e.g. OpenTelemetry)

### Observability

- **Metrics**: Export Prometheus metrics for payment processing (success/failure rates, latency histograms)
- **Alerting**: Monitor `event_publication` table for growing incomplete events
- **Structured logging**: Add MDC context with paymentId, accountId for better log correlation

### Security

- **Authentication**: Add OAuth2/JWT authentication
- **Authorization**: Role-based access control for account operations
- **Rate limiting**: Protect against abuse with rate limiting per client

### Operations

- **Health checks**: Add detailed health indicators for Kafka, database connections
- **Graceful shutdown**: Ensure in-flight payments complete before shutdown
- **Database migrations**: Add Liquibase rollback scripts
- **API versioning**: Implement proper API versioning strategy

### Testing

- **Contract testing**: Add Pact or Spring Cloud Contract for API contracts
- **Chaos testing**: Add chaos engineering tests (e.g., kill Kafka mid-transaction)
- **Performance testing**: Add Gatling or k6 load tests with defined SLOs
