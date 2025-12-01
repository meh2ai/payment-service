package com.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class KafkaConfig {

    public static final String PAYMENT_NOTIFICATIONS_TOPIC = "payment.notifications";

    @Bean
    public NewTopic paymentNotificationsTopic() {
        return TopicBuilder.name(PAYMENT_NOTIFICATIONS_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
