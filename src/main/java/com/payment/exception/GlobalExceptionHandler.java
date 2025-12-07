package com.payment.exception;

import com.payment.api.model.ErrorResponse;
import com.payment.exception.validation.PaymentValidationException;
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
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<ErrorResponse> handlePaymentValidationException(PaymentValidationException ex, HttpServletRequest request) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(ErrorCode errorCode, String message, HttpServletRequest request) {
        HttpStatus status = errorCode.getHttpStatus();

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
