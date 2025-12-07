# Changelog

## [v2.0] - 2025-12-07

### Changed
- Replace event-driven payment processing with Temporal workflows
- Removed Spring Modulith events in favor of Temporal's durable execution model

## [v1.0] - 2025-12-02

### Added
- Initial project setup
- Database schema with Liquibase migrations
- Spring configuration for JPA, Kafka, and scheduling
- Exception handling with error codes
- Account entity, repository, and service
- Payment entity with status, repository with specification
- Domain events for payment lifecycle
- PaymentService with idempotency and PaymentsApiController
- Async payment processing with Spring Modulith events
- ModelMapper configuration
- Pessimistic locking to handle high contention
- OpenAPI specifications

### Fixed
- Prevent double processing of payments by adding optimistic locking to the Payment entity

### Infrastructure
- Integration test base with Testcontainers
- Repository, API, and Kafka event publishing integration tests
- GitHub Actions CI pipeline with JaCoCo report
- Replace Dockerfile with Jib for containerization
