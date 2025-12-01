--liquibase formatted sql

--changeset test:0002 context:local,test
--comment: Test data for local development and stress testing

-- Test accounts for stress testing (matching stress-test.sh defaults)
INSERT INTO account (id, balance, currency, version, created_at, updated_at)
VALUES
    ('8686a341-25a0-43b4-bf3e-2ed5f554452b', 1000000.00, 'EUR', 0, NOW(), NOW()),
    ('41aee2de-014c-48d4-b0e0-b50a708f5250', 1000000.00, 'EUR', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

-- Additional test accounts for manual testing
INSERT INTO account (id, balance, currency, version, created_at, updated_at)
VALUES
    ('e7fb8af3-43d1-4a23-9a85-3f20035b33c3', 5000.00, 'EUR', 0, NOW(), NOW()),
    ('da06a5e1-dd5b-4770-9817-8284c8f27814', 2500.00, 'USD', 0, NOW(), NOW()),
    ('3a6e5541-1e52-4dcb-9dca-e0f1f16e1f57', 0.00, 'EUR', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;
