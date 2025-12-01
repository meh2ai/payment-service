package com.payment.integration.service

import com.payment.config.KafkaConfig
import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.model.Payment
import com.payment.model.PaymentStatus
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import com.payment.service.PaymentProcessor
import groovy.json.JsonSlurper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.support.serializer.JsonDeserializer

import java.time.Duration

class PaymentProcessorIntegrationSpec extends IntegrationTestBase {

    @Autowired
    PaymentRepository paymentRepository

    @Autowired
    AccountRepository accountRepository

    @Autowired
    PaymentProcessor paymentProcessor

    KafkaConsumer<String, byte[]> kafkaConsumer

    UUID senderAccountId
    UUID receiverAccountId

    def setup() {
        paymentRepository.deleteAll()
        accountRepository.deleteAll()

        senderAccountId = UUID.randomUUID()
        receiverAccountId = UUID.randomUUID()

        accountRepository.saveAndFlush(new Account(senderAccountId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.saveAndFlush(new Account(receiverAccountId, new BigDecimal("500.00"), "EUR"))

        kafkaConsumer = createKafkaConsumer()
        kafkaConsumer.subscribe([KafkaConfig.PAYMENT_NOTIFICATIONS_TOPIC])

        // Clear any existing messages
        kafkaConsumer.poll(Duration.ofMillis(500))
    }

    def cleanup() {
        kafkaConsumer?.close()
    }

    def "should publish PaymentCompletedEvent to Kafka after successful payment processing"() {
        given:
        def payment = Payment.create("key-success", senderAccountId, receiverAccountId, new BigDecimal("100.00"), "EUR")
        paymentRepository.saveAndFlush(payment)
        def paymentId = payment.id.toString()

        when:
        paymentProcessor.process(payment.id)

        then: "payment is completed"
        def updated = paymentRepository.findById(payment.id)
        updated.isPresent()
        updated.get().status == PaymentStatus.COMPLETED

        and: "event is published to Kafka"
        def record = pollForRecord(paymentId, Duration.ofSeconds(10))
        record != null

        def payload = new JsonSlurper().parse(record.value())
        payload.paymentId == paymentId
        payload.status == "COMPLETED"
        payload.senderAccountId == senderAccountId.toString()
        payload.receiverAccountId == receiverAccountId.toString()
        payload.amount == "100.00"
        payload.currency == "EUR"
        payload.errorCode == null
        payload.numericErrorCode == null
    }

    def "should publish PaymentCompletedEvent with FAILED status when insufficient balance"() {
        given:
        def payment = Payment.create("key-insufficient", senderAccountId, receiverAccountId, new BigDecimal("5000.00"), "EUR")
        paymentRepository.saveAndFlush(payment)
        def paymentId = payment.id.toString()

        when:
        paymentProcessor.process(payment.id)

        then: "payment is failed"
        def updated = paymentRepository.findById(payment.id)
        updated.isPresent()
        updated.get().status == PaymentStatus.FAILED

        and: "failure event is published to Kafka"
        def record = pollForRecord(paymentId, Duration.ofSeconds(10))
        record != null

        def payload = new JsonSlurper().parse(record.value())
        payload.paymentId == paymentId
        payload.status == "FAILED"
        payload.errorCode == "INSUFFICIENT_BALANCE"
        payload.numericErrorCode == 2004
    }

    def "should update account balances after successful payment"() {
        given:
        def payment = Payment.create("key-balances", senderAccountId, receiverAccountId, new BigDecimal("250.00"), "EUR")
        paymentRepository.saveAndFlush(payment)

        when:
        paymentProcessor.process(payment.id)

        then:
        def sender = accountRepository.findById(senderAccountId).get()
        sender.balance == new BigDecimal("750.00")

        def receiver = accountRepository.findById(receiverAccountId).get()
        receiver.balance == new BigDecimal("750.00")
    }

    def "should not update balances when payment fails"() {
        given:
        def payment = Payment.create("key-no-balance-change", senderAccountId, receiverAccountId, new BigDecimal("5000.00"), "EUR")
        paymentRepository.saveAndFlush(payment)

        when:
        paymentProcessor.process(payment.id)

        then:
        def sender = accountRepository.findById(senderAccountId).get()
        sender.balance == new BigDecimal("1000.00")

        def receiver = accountRepository.findById(receiverAccountId).get()
        receiver.balance == new BigDecimal("500.00")
    }

    def "should skip already completed payment"() {
        given:
        def payment = Payment.create("key-already-done", senderAccountId, receiverAccountId, new BigDecimal("100.00"), "EUR")
        payment.markProcessing()
        payment.markCompleted()
        paymentRepository.saveAndFlush(payment)

        when:
        paymentProcessor.process(payment.id)

        then: "no Kafka event published"
        def record = pollForRecord(payment.id.toString(), Duration.ofSeconds(2))
        record == null

        and: "balances unchanged"
        def sender = accountRepository.findById(senderAccountId).get()
        sender.balance == new BigDecimal("1000.00")
    }

    def "should process multiple payments sequentially"() {
        given:
        def payments = (1..3).collect { i ->
            def payment = Payment.create("key-multi-$i", senderAccountId, receiverAccountId,
                    new BigDecimal("100.00"), "EUR")
            paymentRepository.saveAndFlush(payment)
            payment
        }

        when:
        payments.each { paymentProcessor.process(it.id) }

        then: "all payments completed"
        payments.each { payment ->
            def updated = paymentRepository.findById(payment.id)
            assert updated.isPresent()
            assert updated.get().status == PaymentStatus.COMPLETED
        }

        and: "all events published to Kafka"
        def receivedIds = [] as Set
        def deadline = System.currentTimeMillis() + 10_000
        while (receivedIds.size() < 3 && System.currentTimeMillis() < deadline) {
            def records = kafkaConsumer.poll(Duration.ofMillis(500))
            records.each { receivedIds << it.key() }
        }
        receivedIds.size() == 3
        payments.every { receivedIds.contains(it.id.toString()) }

        and: "balances updated correctly"
        def sender = accountRepository.findById(senderAccountId).get()
        sender.balance == new BigDecimal("700.00")

        def receiver = accountRepository.findById(receiverAccountId).get()
        receiver.balance == new BigDecimal("800.00")
    }

    private KafkaConsumer<String, String> createKafkaConsumer() {
        def props = new Properties()
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-${UUID.randomUUID()}".toString())
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.name)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.name)
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*")

        return new KafkaConsumer<>(props)
    }

    private ConsumerRecord<String, byte[]> pollForRecord(String key, Duration timeout) {
        ConsumerRecord<String, byte[]> foundRecord = null
        try {
            Awaitility.await()
                    .atMost(timeout)
                    .pollInterval(Duration.ofMillis(500))
                    .until {
                        def records = kafkaConsumer.poll(Duration.ofMillis(100))
                        def record = records.find { it.key() == key }
                        if (record != null) {
                            foundRecord = record as ConsumerRecord<String, byte[]>
                            return true
                        }
                        return false
                    }
        } catch (Exception ignored) {
        }

        return foundRecord
    }
}
