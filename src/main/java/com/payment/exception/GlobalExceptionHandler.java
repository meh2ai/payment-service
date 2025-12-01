package com.payment.exception;

import com.payment.api.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException ex, HttpServletRequest request) {
        HttpStatus status = mapToHttpStatus(ex.getErrorCode());
        return buildErrorResponse(status, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request
        );
    }

    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case PAYMENT_NOT_FOUND, ACCOUNT_NOT_FOUND, SENDER_ACCOUNT_NOT_FOUND, RECEIVER_ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INSUFFICIENT_BALANCE, PAYMENT_PROCESSING_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case DUPLICATE_PAYMENT -> HttpStatus.CONFLICT;
            case VALIDATION_ERROR, INVALID_AMOUNT, SAME_ACCOUNT, INVALID_CURRENCY -> HttpStatus.BAD_REQUEST;
            case INTERNAL_ERROR, SERVICE_UNAVAILABLE -> HttpStatus.INTERNAL_SERVER_ERROR;

        };
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status, ErrorCode errorCode, String message, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(OffsetDateTime.now());
        error.setStatus(status.value());
        error.setError(status.getReasonPhrase());
        error.setErrorCode(errorCode.name());
        error.setNumericCode(errorCode.getNumericCode());
        error.setMessage(message);
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
