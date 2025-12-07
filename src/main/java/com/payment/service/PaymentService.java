package com.payment.service;

import com.payment.api.model.PaymentAcceptedResponse;
import com.payment.api.model.PaymentListResponse;
import com.payment.api.model.PaymentRequest;
import com.payment.api.model.PaymentResponse;
import com.payment.config.TemporalConfig;
import com.payment.exception.ResourceNotFoundException;
import com.payment.exception.validation.PaymentValidationException;
import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import com.payment.repository.AccountRepository;
import com.payment.repository.PaymentRepository;
import com.payment.repository.PaymentSpecification;
import com.payment.temporal.workflow.PaymentWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final WorkflowClient workflowClient;
    private final ModelMapper modelMapper;

    @Transactional
    public PaymentAcceptedResponse submitPayment(PaymentRequest request, String idempotencyKey) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            log.info("Duplicate payment request with idempotency key: {}", idempotencyKey);
            return toAcceptedResponse(existing.get());
        }

        validatePaymentRequest(request);

        Payment payment = Payment.create(
            idempotencyKey,
            request.getSenderAccountId(),
            request.getReceiverAccountId(),
            new BigDecimal(request.getAmount()),
            request.getCurrency()
        );

        paymentRepository.save(payment);
        log.info("Payment created: {}", payment.getId());

        // Start Temporal Workflow
        PaymentWorkflow workflow = workflowClient.newWorkflowStub(
            PaymentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.PAYMENT_TASK_QUEUE)
                .setWorkflowId(payment.getId().toString())
                .build()
        );

        WorkflowClient.start(workflow::processPayment, payment.getId());

        return toAcceptedResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> ResourceNotFoundException.paymentNotFound(paymentId));
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentListResponse listPayments(UUID senderAccountId, PaymentStatus status, Pageable pageable) {
        Specification<Payment> spec = PaymentSpecification.searchBy(senderAccountId, status);
        Page<Payment> page = paymentRepository.findAll(spec, pageable);

        PaymentListResponse response = new PaymentListResponse();
        response.setContent(page.getContent().stream()
            .map(this::toPaymentResponse)
            .toList());
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        return response;
    }

    private void validatePaymentRequest(PaymentRequest request) {
        if (request.getSenderAccountId().equals(request.getReceiverAccountId())) {
            throw PaymentValidationException.sameAccount(request.getSenderAccountId());
        }

        if (!accountRepository.existsById(request.getSenderAccountId())) {
            throw ResourceNotFoundException.senderAccountNotFound(request.getSenderAccountId());
        }

        if (!accountRepository.existsById(request.getReceiverAccountId())) {
            throw ResourceNotFoundException.receiverAccountNotFound(request.getReceiverAccountId());
        }

        BigDecimal amount = new BigDecimal(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentValidationException.invalidAmount(amount);
        }
    }

    private PaymentAcceptedResponse toAcceptedResponse(Payment payment) {
        return modelMapper.map(payment, PaymentAcceptedResponse.class);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return modelMapper.map(payment, PaymentResponse.class);
    }
}
