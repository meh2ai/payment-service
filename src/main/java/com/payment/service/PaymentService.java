package com.payment.service;

import com.payment.api.model.PaymentAcceptedResponse;
import com.payment.api.model.PaymentListResponse;
import com.payment.api.model.PaymentRequest;
import com.payment.api.model.PaymentResponse;
import com.payment.event.PaymentCreatedEvent;
import com.payment.exception.PaymentException;
import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import com.payment.repository.AccountRepository;
import com.payment.repository.PaymentRepository;
import com.payment.repository.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
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

        eventPublisher.publishEvent(new PaymentCreatedEvent(
            payment.getId(),
            payment.getSenderAccountId(),
            payment.getReceiverAccountId(),
            payment.getAmount(),
            payment.getCurrency()
        ));

        return toAcceptedResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> PaymentException.paymentNotFound(paymentId));
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
            throw PaymentException.sameAccount(request.getSenderAccountId());
        }

        if (!accountRepository.existsById(request.getSenderAccountId())) {
            throw PaymentException.senderAccountNotFound(request.getSenderAccountId());
        }

        if (!accountRepository.existsById(request.getReceiverAccountId())) {
            throw PaymentException.receiverAccountNotFound(request.getReceiverAccountId());
        }

        BigDecimal amount = new BigDecimal(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentException.invalidAmount(amount);
        }
    }

    private PaymentAcceptedResponse toAcceptedResponse(Payment payment) {
        return modelMapper.map(payment, PaymentAcceptedResponse.class);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return modelMapper.map(payment, PaymentResponse.class);
    }
}
