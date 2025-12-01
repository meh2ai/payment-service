package com.payment.controller;

import com.payment.api.PaymentsApi;
import com.payment.api.model.PaymentAcceptedResponse;
import com.payment.api.model.PaymentListResponse;
import com.payment.api.model.PaymentRequest;
import com.payment.api.model.PaymentResponse;
import com.payment.model.PaymentStatus;
import com.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentsApiController implements PaymentsApi {

    private final PaymentService paymentService;

    @Override
    public ResponseEntity<PaymentAcceptedResponse> submitPayment(
        String idempotencyKey,
        PaymentRequest paymentRequest) {

        PaymentAcceptedResponse response = paymentService.submitPayment(paymentRequest, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Override
    public ResponseEntity<PaymentResponse> getPayment(UUID paymentId) {
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentListResponse> listPayments(
        UUID senderAccountId,
        com.payment.api.model.PaymentStatus status,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PaymentStatus domainStatus = status != null ? PaymentStatus.valueOf(status.name()) : null;
        PaymentListResponse response = paymentService.listPayments(senderAccountId, domainStatus, pageable);
        return ResponseEntity.ok(response);
    }
}
