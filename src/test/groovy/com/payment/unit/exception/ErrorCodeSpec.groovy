package com.payment.unit.exception

import com.payment.exception.ErrorCode
import spock.lang.Specification
import spock.lang.Unroll

class ErrorCodeSpec extends Specification {

    @Unroll
    def "error code #errorCode should have numeric code #expectedNumericCode"() {
        expect:
        errorCode.numericCode == expectedNumericCode
        errorCode.defaultMessage != null
        !errorCode.defaultMessage.isEmpty()

        where:
        errorCode                          | expectedNumericCode
        ErrorCode.PAYMENT_NOT_FOUND        | 1001
        ErrorCode.DUPLICATE_PAYMENT        | 1002
        ErrorCode.PAYMENT_PROCESSING_FAILED| 1003
        ErrorCode.ACCOUNT_NOT_FOUND        | 2001
        ErrorCode.SENDER_ACCOUNT_NOT_FOUND | 2002
        ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND | 2003
        ErrorCode.INSUFFICIENT_BALANCE     | 2004
        ErrorCode.VALIDATION_ERROR         | 3001
        ErrorCode.INVALID_AMOUNT           | 3002
        ErrorCode.INVALID_CURRENCY         | 3003
        ErrorCode.INTERNAL_ERROR           | 5001
        ErrorCode.SERVICE_UNAVAILABLE      | 5002
    }

    def "payment error codes should be in 1xxx range"() {
        expect:
        ErrorCode.PAYMENT_NOT_FOUND.numericCode >= 1000
        ErrorCode.PAYMENT_NOT_FOUND.numericCode < 2000
        ErrorCode.DUPLICATE_PAYMENT.numericCode >= 1000
        ErrorCode.DUPLICATE_PAYMENT.numericCode < 2000
    }

    def "account error codes should be in 2xxx range"() {
        expect:
        ErrorCode.ACCOUNT_NOT_FOUND.numericCode >= 2000
        ErrorCode.ACCOUNT_NOT_FOUND.numericCode < 3000
        ErrorCode.INSUFFICIENT_BALANCE.numericCode >= 2000
        ErrorCode.INSUFFICIENT_BALANCE.numericCode < 3000
    }

    def "validation error codes should be in 3xxx range"() {
        expect:
        ErrorCode.VALIDATION_ERROR.numericCode >= 3000
        ErrorCode.VALIDATION_ERROR.numericCode < 4000
    }

    def "system error codes should be in 5xxx range"() {
        expect:
        ErrorCode.INTERNAL_ERROR.numericCode >= 5000
        ErrorCode.INTERNAL_ERROR.numericCode < 6000
    }
}
