--liquibase formatted sql

--changeset payment-service:remove-event-publication
DROP TABLE event_publication;
