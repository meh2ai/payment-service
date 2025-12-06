--liquibase formatted sql

--changeset payment-service:add-payment-version
ALTER TABLE payment ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
