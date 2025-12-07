# Payment Service

A Payment Service REST API built with Spring Boot and Temporal for reliable workflow orchestration.
The service processes payments with transactional integrity, handles concurrency to prevent double-spending, persists to PostgreSQL, and
sends asynchronous notifications via Kafka.

## Tech Stack

- Java 21
- Spring Boot 3.5
- Temporal.io with Spring Boot Starter (workflow orchestration)
- PostgreSQL 18
- Apache Kafka
- Liquibase (database migrations)
- Spock Framework with Groovy 4 (testing)
- Testcontainers (integration testing)

## Quick Start

### Prerequisites

- JDK 21+
- Docker & Docker Compose

### Run Dependencies

```bash
docker-compose up -d
```

This starts:

- PostgreSQL (port 5432)
- Kafka (port 9092)
- Temporal Server (port 7233)
- Temporal UI (port 8081) - accessible at http://localhost:8081

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

| Method | Endpoint                | Description                                       |
|--------|-------------------------|---------------------------------------------------|
| POST   | `/api/v1/accounts`      | Create a new account                              |
| GET    | `/api/v1/accounts/{id}` | Get account                                       |
| POST   | `/api/v1/payments`      | Submit a new payment                              |
| GET    | `/api/v1/payments/{id}` | Get payment by ID                                 |
| GET    | `/api/v1/payments`      | List payments (with filters, pagination, sorting) |
| GET    | `/actuator/health`      | Health check                                      |

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

## Manual Testing

### Start the Environment

```bash
# Start dependencies
docker-compose up -d

# Wait for services to be healthy, then start the application
./gradlew bootRun
```

### Verify Kafka Messages

Open a separate terminal to consume Kafka messages:

```bash
docker exec payment-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.notifications \
  --property print.key=true
```

### Happy Path: Successful Payment

Uses the pre-loaded test accounts (each with 1,000,000 EUR):

```bash
# Create a payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-success-001" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "100.00",
    "currency": "EUR"
  }'

# Response: 202 Accepted with payment ID
```

Check payment status (should be COMPLETED):

```bash
curl http://localhost:8080/api/v1/payments/{paymentId}
```

Verify balances updated:

```bash
curl http://localhost:8080/api/v1/accounts/8686a341-25a0-43b4-bf3e-2ed5f554452b
# Expected balance: 999900.00

curl http://localhost:8080/api/v1/accounts/41aee2de-014c-48d4-b0e0-b50a708f5250
# Expected balance: 1000100.00
```

Kafka consumer should show a success event with `"status":"COMPLETED"`.

### Unhappy Path: Insufficient Balance

Create an account with low balance:

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "11111111-1111-1111-1111-111111111111",
    "balance": "50.00",
    "currency": "EUR"
  }'
```

Attempt payment exceeding balance:

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-insufficient-001" \
  -d '{
    "senderAccountId": "11111111-1111-1111-1111-111111111111",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "100.00",
    "currency": "EUR"
  }'
```

Check payment status (should be FAILED with INSUFFICIENT_BALANCE):

```bash
curl http://localhost:8080/api/v1/payments/{paymentId}
```

Kafka consumer should show a failure event with `"errorCode":"INSUFFICIENT_BALANCE"`.

### Unhappy Path: Sender Account Not Found

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-sender-not-found-001" \
  -d '{
    "senderAccountId": "00000000-0000-0000-0000-000000000000",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "100.00",
    "currency": "EUR"
  }'

# Response: 404 Not Found with SENDER_ACCOUNT_NOT_FOUND (2002)
```

### Unhappy Path: Receiver Account Not Found

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-receiver-not-found-001" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "00000000-0000-0000-0000-000000000000",
    "amount": "100.00",
    "currency": "EUR"
  }'

# Response: 404 Not Found with RECEIVER_ACCOUNT_NOT_FOUND (2003)
```

### Unhappy Path: Same Account

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-same-account-001" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "amount": "100.00",
    "currency": "EUR"
  }'

# Response: 400 Bad Request with SAME_ACCOUNT (2005)
```

### Unhappy Path: Invalid Amount

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-invalid-amount-001" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "-50.00",
    "currency": "EUR"
  }'

# Response: 400 Bad Request with INVALID_AMOUNT (3002)
```

### Idempotency: Duplicate Payment

Submit the same payment twice with the same idempotency key:

```bash
# First request - creates payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-idempotent-001" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "25.00",
    "currency": "EUR"
  }'

# Second request - returns same payment ID, no duplicate created
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-idempotent-001" \
  -d '{
    "senderAccountId": "8686a341-25a0-43b4-bf3e-2ed5f554452b",
    "receiverAccountId": "41aee2de-014c-48d4-b0e0-b50a708f5250",
    "amount": "25.00",
    "currency": "EUR"
  }'

# Both return 202 with the same payment ID
```

## Architecture

### Workflow Orchestration with Temporal

The service uses Temporal for reliable, durable workflow execution:

1. Client submits payment with idempotency key
2. Payment saved as PENDING, returns `202 Accepted` immediately
3. `PaymentWorkflow` is started asynchronously via Temporal
4. `LedgerActivities.executeTransfer()` executes the fund transfer in a transaction
5. On business failure (e.g., insufficient funds), payment is marked as FAILED
6. `NotificationActivities.publishCompletionEvent()` publishes to Kafka
7. Workflow completes

```
┌─────────┐     ┌─────────────────┐     ┌──────────────────┐
│  Client │────▶│ PaymentService  │────▶│ Temporal Server  │
└─────────┘     └─────────────────┘     └──────────────────┘
                       │                         │
                       ▼                         ▼
                ┌─────────────┐          ┌──────────────┐
                │  PostgreSQL │◀─────────│    Worker    │
                └─────────────┘          └──────────────┘
                                                │
                                                ▼
                                           ┌───────────┐
                                           │   Kafka   │
                                           └───────────┘
```

### Temporal Components

| Component                | Description                                       |
|--------------------------|---------------------------------------------------|
| `PaymentWorkflow`        | Orchestrates the payment processing steps         |
| `LedgerActivities`       | Executes fund transfer with database transactions |
| `NotificationActivities` | Publishes completion events to Kafka              |

### Guaranteed Execution

- **Durable workflows**: Temporal persists workflow state; survives process restarts
- **Automatic retries**: Activities have configurable retry policies with exponential backoff
- **Failure handling**: Business errors (e.g., insufficient funds) are handled gracefully
- **Visibility**: Monitor workflows via Temporal UI at http://localhost:8081

### Concurrency Control

- Optimistic locking with `@Version` on Account and Payment entities
- Lock ordering prevents deadlocks when transferring between accounts
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

| Variable                  | Default        | Description        |
|---------------------------|----------------|--------------------|
| `DB_HOST`                 | localhost      | PostgreSQL host    |
| `DB_PORT`                 | 5432           | PostgreSQL port    |
| `DB_NAME`                 | payments       | Database name      |
| `DB_USER`                 | postgres       | Database user      |
| `DB_PASSWORD`             | postgres       | Database password  |
| `KAFKA_BOOTSTRAP_SERVERS` | localhost:9092 | Kafka servers      |
| `TEMPORAL_TARGET`         | 127.0.0.1:7233 | Temporal server    |
| `TEMPORAL_NAMESPACE`      | default        | Temporal namespace |

## Project Structure

```
com.payment/
├── config/              # Spring configuration
├── controller/          # REST controllers
├── event/               # Kafka events
├── exception/           # Exception handling
├── model/               # JPA entities
├── repository/          # Data access
├── service/             # Business logic
└── temporal/
    ├── activity/        # Temporal activities
    └── workflow/        # Temporal workflows
```

## Future Improvements

The following improvements could be implemented to make the service more production-ready:

### Reliability & Resilience

- **Dead Letter Queue (DLQ)**: Route failed Kafka messages to DLQ after exhausting retries
- **Circuit breaker**: Add circuit breaker for external service calls

### Observability

- **Metrics**: Export Prometheus metrics for payment processing (success/failure rates, latency histograms)
- **Distributed tracing**: Integrate with OpenTelemetry for end-to-end tracing
- **Structured logging**: Add MDC context with paymentId, workflowId for better log correlation

### Security

- **Authentication**: Add OAuth2/JWT authentication
- **Authorization**: Role-based access control for account operations
- **Rate limiting**: Protect against abuse with rate limiting per client

### Operations

- **Health checks**: Add detailed health indicators for Temporal, Kafka, database connections
- **Graceful shutdown**: Ensure in-flight workflows complete before shutdown
- **Database migrations**: Add Liquibase rollback scripts
- **API versioning**: Implement proper API versioning strategy

### Testing

- **Contract testing**: Add Pact or Spring Cloud Contract for API contracts
- **Chaos testing**: Add chaos engineering tests (e.g., kill Temporal worker mid-workflow)
- **Performance testing**: Add Gatling or k6 load tests with defined SLOs
