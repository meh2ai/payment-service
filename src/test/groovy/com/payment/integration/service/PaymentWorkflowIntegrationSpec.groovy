package com.payment.integration.service

import com.payment.config.KafkaConfig
import com.payment.config.TemporalConfig
import com.payment.event.PaymentCompletedEvent
import com.payment.exception.ErrorCode
import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.model.Payment
import com.payment.model.PaymentStatus
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import com.payment.temporal.workflow.PaymentWorkflow
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.support.serializer.JsonDeserializer

import java.time.Duration
import java.util.concurrent.CompletableFuture

class PaymentWorkflowIntegrationSpec extends IntegrationTestBase {

    @Autowired
    PaymentRepository paymentRepository

    @Autowired
    AccountRepository accountRepository

    @Autowired
    WorkflowClient workflowClient

    KafkaConsumer<String, PaymentCompletedEvent> kafkaConsumer

    def setup() {
        paymentRepository.deleteAll()
        accountRepository.deleteAll()

        kafkaConsumer = createKafkaConsumer()
        kafkaConsumer.subscribe([KafkaConfig.PAYMENT_NOTIFICATIONS_TOPIC])

        // Clear any existing messages
        kafkaConsumer.poll(Duration.ofMillis(500))
    }

    def cleanup() {
        kafkaConsumer?.close()
    }

    def "should process payment via Temporal workflow"() {
        given: "accounts and pending payment"
        def senderId = UUID.randomUUID()
        def receiverId = UUID.randomUUID()
        accountRepository.save(new Account(senderId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.save(new Account(receiverId, new BigDecimal("500.00"), "EUR"))

        def payment = Payment.create("key-success", senderId, receiverId, new BigDecimal("100.00"), "EUR")
        paymentRepository.save(payment)

        when: "workflow is started"
        executeWorkflow(payment.getId())

        then: "payment is completed in DB"
        def updatedPayment = paymentRepository.findById(payment.getId()).get()
        updatedPayment.status == PaymentStatus.COMPLETED

        and: "balances are updated"
        accountRepository.findById(senderId).get().balance == new BigDecimal("900.00")
        accountRepository.findById(receiverId).get().balance == new BigDecimal("600.00")
    }

    def "should fail payment when insufficient funds"() {
        given: "accounts with insufficient funds"
        def senderId = UUID.randomUUID()
        def receiverId = UUID.randomUUID()
        accountRepository.save(new Account(senderId, new BigDecimal("50.00"), "EUR"))
        accountRepository.save(new Account(receiverId, new BigDecimal("500.00"), "EUR"))

        def payment = Payment.create("key-insufficient", senderId, receiverId, new BigDecimal("100.00"), "EUR")
        paymentRepository.save(payment)

        when: "workflow is started"
        executeWorkflow(payment.getId())

        then: "payment is failed in DB"
        def updatedPayment = paymentRepository.findById(payment.getId()).get()
        updatedPayment.status == PaymentStatus.FAILED
        updatedPayment.errorCode.name() == "INSUFFICIENT_BALANCE"

        and: "balances are unchanged"
        accountRepository.findById(senderId).get().balance == new BigDecimal("50.00")
        accountRepository.findById(receiverId).get().balance == new BigDecimal("500.00")
    }

    def "should publish PaymentCompletedEvent to Kafka after successful payment"() {
        given: "accounts and pending payment"
        def senderId = UUID.randomUUID()
        def receiverId = UUID.randomUUID()
        accountRepository.save(new Account(senderId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.save(new Account(receiverId, new BigDecimal("500.00"), "EUR"))

        def payment = Payment.create("key-kafka-success", senderId, receiverId, new BigDecimal("100.00"), "EUR")
        paymentRepository.save(payment)
        def paymentId = payment.id

        when: "workflow is executed"
        executeWorkflow(payment.getId())

        then: "payment is completed"
        def updated = paymentRepository.findById(payment.getId())
        updated.isPresent()
        updated.get().status == PaymentStatus.COMPLETED

        and: "event is published to Kafka"
        def record = pollForRecord(paymentId.toString(), Duration.ofSeconds(10))
        record != null

        def payload = record.value()
        payload.paymentId == paymentId
        payload.status == PaymentStatus.COMPLETED
        payload.senderAccountId == senderId
        payload.receiverAccountId == receiverId
        payload.amount == 100.00
        payload.currency == "EUR"
        payload.errorCode == null
    }

    def "should publish PaymentCompletedEvent with FAILED status when insufficient balance"() {
        given: "accounts with insufficient funds"
        def senderId = UUID.randomUUID()
        def receiverId = UUID.randomUUID()
        accountRepository.save(new Account(senderId, new BigDecimal("50.00"), "EUR"))
        accountRepository.save(new Account(receiverId, new BigDecimal("500.00"), "EUR"))

        def payment = Payment.create("key-kafka-insufficient", senderId, receiverId, new BigDecimal("100.00"), "EUR")
        paymentRepository.save(payment)
        def paymentId = payment.id

        when: "workflow is executed"
        executeWorkflow(payment.getId())

        then: "payment is failed"
        def updated = paymentRepository.findById(payment.getId())
        updated.isPresent()
        updated.get().status == PaymentStatus.FAILED

        and: "failure event is published to Kafka"
        def record = pollForRecord(paymentId.toString(), Duration.ofSeconds(10))
        record != null

        def payload = record.value()
        payload.paymentId == paymentId
        payload.status == PaymentStatus.FAILED
        payload.errorCode == ErrorCode.INSUFFICIENT_BALANCE
    }

    def "should publish events for multiple payments"() {
        given: "accounts"
        def senderId = UUID.randomUUID()
        def receiverId = UUID.randomUUID()
        accountRepository.save(new Account(senderId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.save(new Account(receiverId, new BigDecimal("500.00"), "EUR"))

        and: "multiple payments"
        def payments = (1..3).collect { i ->
            def payment = Payment.create("key-multi-$i", senderId, receiverId, new BigDecimal("100.00"), "EUR")
            paymentRepository.save(payment)
            payment
        }

        when: "all workflows are executed"
        payments.each { executeWorkflow(it.id) }

        then: "all payments completed"
        payments.each { payment ->
            def updated = paymentRepository.findById(payment.id)
            assert updated.isPresent()
            assert updated.get().status == PaymentStatus.COMPLETED
        }

        and: "all events published to Kafka"
        def receivedIds = [] as Set
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .until {
                    def records = kafkaConsumer.poll(Duration.ofMillis(100))
                    records.each { receivedIds << it.key() }
                    receivedIds.size() >= 3
                }
        payments.every { receivedIds.contains(it.id.toString()) }
    }

    def "should prevent double processing of the same payment"() {
        given: "a sender and receiver account"
        def senderId = UUID.randomUUID()
        def receiverId = UUID.randomUUID()
        accountRepository.save(new Account(senderId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.save(new Account(receiverId, new BigDecimal("0.00"), "EUR"))

        and: "a pending payment"
        def paymentAmount = new BigDecimal("100.00")
        def payment = Payment.create("key-concurrent", senderId, receiverId, paymentAmount, "EUR")
        paymentRepository.save(payment)

        when: "two threads try to process the same payment concurrently"
        def futures = (1..2).collect {
            CompletableFuture.runAsync {
                try {
                    executeWorkflow(payment.id)
                } catch (Exception ignored) {
                    // One may fail with WorkflowExecutionAlreadyStarted, which is expected
                }
            }
        }
        futures*.join()

        then: "the payment should be processed exactly once"
        def updatedSender = accountRepository.findById(senderId).get()
        def updatedReceiver = accountRepository.findById(receiverId).get()
        def updatedPayment = paymentRepository.findById(payment.id).get()

        updatedSender.balance == new BigDecimal("900.00")
        updatedReceiver.balance == new BigDecimal("100.00")
        updatedPayment.status == PaymentStatus.COMPLETED
    }

    private void executeWorkflow(UUID paymentId) {
        def workflow = workflowClient.newWorkflowStub(
                PaymentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TemporalConfig.PAYMENT_TASK_QUEUE)
                        .setWorkflowId(paymentId.toString())
                        .build()
        )
        workflow.processPayment(paymentId)
    }

    private KafkaConsumer<String, PaymentCompletedEvent> createKafkaConsumer() {
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

    private ConsumerRecord<String, PaymentCompletedEvent> pollForRecord(String key, Duration timeout) {
        ConsumerRecord<String, PaymentCompletedEvent> foundRecord = null
        try {
            Awaitility.await()
                    .atMost(timeout)
                    .pollInterval(Duration.ofMillis(500))
                    .until {
                        def records = kafkaConsumer.poll(Duration.ofMillis(100))
                        def record = records.find { it.key() == key }
                        if (record != null) {
                            foundRecord = record as ConsumerRecord<String, PaymentCompletedEvent>
                            return true
                        }
                        return false
                    }
        } catch (Exception ignored) {
        }

        return foundRecord
    }
}
