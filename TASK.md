# Payment Service Design Task

## Short Summary
Design and build a Payment Service that allows users to create payments using a REST API.

## Focus Areas
- High Availability
- Transactional Processing
- Error Handling

## Task Requirements
- Use Git as a version control system and upload your code into a public GitHub repository.
- Use Spring Boot with Maven or Gradle, and your preferred Java version.
- No UI implementation is required for this task.

## Service Specifications
- Perform balance check before processing payments.
- Handle concurrency (e.g., prevent double spending / double notifications).
- Save transactions into a PostgreSQL table.
- Use Kafka to asynchronously send a notification of the transaction to the sender.

## Implementation Extras
- Implement a fault-tolerant system.
- Write tests to ensure functionality.
- Document the REST API.
