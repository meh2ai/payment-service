package com.payment.unit.exception

import com.payment.exception.ErrorCode
import com.payment.exception.GlobalExceptionHandler
import com.payment.exception.PaymentException
import com.payment.exception.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GlobalExceptionHandlerSpec extends Specification {

    @Subject
    GlobalExceptionHandler handler = new GlobalExceptionHandler()

    HttpServletRequest request = Mock()

    def setup() {
        request.getRequestURI() >> "/api/v1/payments"
    }

    @Unroll
    def "should map PaymentException with #errorCode to HTTP #expectedStatus"() {
        given:
        def exception = new PaymentException(errorCode, "Test message")

        when:
        def response = handler.handlePaymentException(exception, request)

        then:
        response.statusCode == expectedStatus
        response.body.errorCode == errorCode.name()
        response.body.numericCode == errorCode.numericCode
        response.body.message == "Test message"

        where:
        errorCode                            | expectedStatus
        ErrorCode.DUPLICATE_PAYMENT          | HttpStatus.CONFLICT
        ErrorCode.PAYMENT_PROCESSING_FAILED  | HttpStatus.UNPROCESSABLE_ENTITY
    }

    @Unroll
    def "should map ResourceNotFoundException with #errorCode to HTTP NOT_FOUND"() {
        given:
        def exception = new ResourceNotFoundException(errorCode, "Test message")

        when:
        def response = handler.handleResourceNotFoundException(exception, request)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.errorCode == errorCode.name()
        response.body.numericCode == errorCode.numericCode
        response.body.message == "Test message"

        where:
        errorCode << [
            ErrorCode.PAYMENT_NOT_FOUND,
            ErrorCode.ACCOUNT_NOT_FOUND,
            ErrorCode.SENDER_ACCOUNT_NOT_FOUND,
            ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND
        ]
    }

    def "should handle ResourceNotFoundException with all fields populated"() {
        given:
        def exception = ResourceNotFoundException.paymentNotFound(UUID.randomUUID())

        when:
        def response = handler.handleResourceNotFoundException(exception, request)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.timestamp != null
        response.body.status == 404
        response.body.error == "Not Found"
        response.body.errorCode == "PAYMENT_NOT_FOUND"
        response.body.numericCode == 1001
        response.body.path == "/api/v1/payments"
    }

    def "should handle MethodArgumentNotValidException"() {
        given:
        def bindingResult = Mock(BindingResult)
        def fieldError1 = new FieldError("request", "amount", "must not be null")
        def fieldError2 = new FieldError("request", "currency", "must be 3 characters")
        bindingResult.getFieldErrors() >> [fieldError1, fieldError2]

        def exception = new MethodArgumentNotValidException(null, bindingResult)

        when:
        def response = handler.handleValidation(exception, request)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.status == 400
        response.body.errorCode == "VALIDATION_ERROR"
        response.body.numericCode == 3001
        response.body.message.contains("amount")
        response.body.message.contains("currency")
    }

    def "should handle generic Exception"() {
        given:
        def exception = new RuntimeException("Something went wrong")

        when:
        def response = handler.handleGeneric(exception, request)

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.status == 500
        response.body.errorCode == "INTERNAL_ERROR"
        response.body.numericCode == 5001
        response.body.message == "An unexpected error occurred"
    }
}
