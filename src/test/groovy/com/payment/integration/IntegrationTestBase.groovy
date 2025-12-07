package com.payment.integration

import com.payment.PaymentServiceApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import org.testcontainers.utility.DockerImageName
import spock.lang.Specification

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [PaymentServiceApplication.class]
)
@ActiveProfiles("test")
@Testcontainers
abstract class IntegrationTestBase extends Specification {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("payments_test")
            .withUsername("test")
            .withPassword("test")

    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.5"))

    static {
        postgres.start()
        kafka.start()
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
        registry.add("spring.datasource.username", postgres::getUsername)
        registry.add("spring.datasource.password", postgres::getPassword)
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
    }
}
