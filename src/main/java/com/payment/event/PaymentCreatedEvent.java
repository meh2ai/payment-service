package com.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreatedEvent(
    UUID paymentId,
    UUID senderAccountId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency
) {
}
