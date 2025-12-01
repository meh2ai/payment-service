--liquibase formatted sql

--changeset payment-service:1
CREATE TABLE account (
    id UUID PRIMARY KEY,
    balance DECIMAL(19,2) NOT NULL CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

--changeset payment-service:2
CREATE TABLE payment (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    sender_account_id UUID NOT NULL REFERENCES account(id),
    receiver_account_id UUID NOT NULL REFERENCES account(id),
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(50),
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

--changeset payment-service:3
CREATE INDEX idx_payment_sender_account_id ON payment(sender_account_id);
CREATE INDEX idx_payment_receiver_account_id ON payment(receiver_account_id);
CREATE INDEX idx_payment_status ON payment(status);

--changeset payment-service:4
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP NOT NULL,
    completion_date TIMESTAMP
);

CREATE INDEX idx_event_publication_incomplete ON event_publication(completion_date) WHERE completion_date IS NULL;
