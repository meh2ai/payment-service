package com.payment.unit.exception

import com.payment.exception.ErrorCode
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Unroll

class ErrorCodeSpec extends Specification {

    @Unroll
    def "error code #errorCode should have numeric code #expectedNumericCode and HTTP status #expectedHttpStatus"() {
        expect:
        errorCode.numericCode == expectedNumericCode
        errorCode.httpStatus == expectedHttpStatus

        where:
        errorCode                            | expectedNumericCode | expectedHttpStatus
        ErrorCode.PAYMENT_NOT_FOUND          | 1001                | HttpStatus.NOT_FOUND
        ErrorCode.DUPLICATE_PAYMENT          | 1002                | HttpStatus.CONFLICT
        ErrorCode.PAYMENT_PROCESSING_FAILED  | 1003                | HttpStatus.UNPROCESSABLE_ENTITY
        ErrorCode.ACCOUNT_NOT_FOUND          | 2001                | HttpStatus.NOT_FOUND
        ErrorCode.SENDER_ACCOUNT_NOT_FOUND   | 2002                | HttpStatus.NOT_FOUND
        ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND | 2003                | HttpStatus.NOT_FOUND
        ErrorCode.INSUFFICIENT_BALANCE       | 2004                | HttpStatus.UNPROCESSABLE_ENTITY
        ErrorCode.SAME_ACCOUNT               | 2005                | HttpStatus.BAD_REQUEST
        ErrorCode.VALIDATION_ERROR           | 3001                | HttpStatus.BAD_REQUEST
        ErrorCode.INVALID_AMOUNT             | 3002                | HttpStatus.BAD_REQUEST
        ErrorCode.INTERNAL_ERROR             | 5001                | HttpStatus.INTERNAL_SERVER_ERROR
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
