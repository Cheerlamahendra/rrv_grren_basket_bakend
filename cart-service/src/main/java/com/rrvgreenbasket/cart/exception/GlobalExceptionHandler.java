
package com.rrvgreenbasket.cart.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<CartErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {

        CartErrorResponse response = new CartErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<CartErrorResponse> handleCartNotFound(
            CartNotFoundException ex, HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<CartErrorResponse> handleCartItemNotFound(
            CartItemNotFoundException ex, HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<CartErrorResponse> handleProductUnavailable(
            ProductUnavailableException ex, HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<CartErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<CartErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex, HttpServletRequest request) {

        log.error("Downstream service unavailable: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CartErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Input validation failed",
                request,
                errors
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<CartErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {

        log.warn("Concurrent cart modification: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.CONFLICT,
                "Your cart was modified by another request. Please refresh and try again.",
                request,
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CartErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn("Cart database constraint violation: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.CONFLICT,
                "The cart operation conflicts with existing data. Please refresh and try again.",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CartErrorResponse> handleGeneral(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected Cart Service error", ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request,
                null
        );
    }
}
