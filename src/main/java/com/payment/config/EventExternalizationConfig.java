package com.payment.config;

import com.payment.event.PaymentCompletedEvent;
import com.payment.event.PaymentNotification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Configuration for externalizing domain events to Kafka using Spring Modulith.
 * <p>
 * Spring Modulith provides a transactional outbox pattern implementation that ensures reliable event delivery.
 * When a domain event is published via {@link org.springframework.context.ApplicationEventPublisher},
 * it is persisted to the {@code event_publication} table within the same database transaction as the business operation.
 * After the transaction commits, Spring Modulith asynchronously sends the event to Kafka and marks it as completed.
 *
 */
@Configuration
public class EventExternalizationConfig {

    @Bean
    public EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
            .select(event -> event instanceof PaymentCompletedEvent)
            .route(
                PaymentCompletedEvent.class,
                event -> RoutingTarget.forTarget(KafkaConfig.PAYMENT_NOTIFICATIONS_TOPIC).andKey(event.paymentId().toString())
            )
            .mapping(
                PaymentCompletedEvent.class,
                PaymentNotification::fromCompletedEvent
            )
            .build();
    }
}
