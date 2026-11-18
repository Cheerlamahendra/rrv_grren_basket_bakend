package com.rrvgreenbasket.delivery.exception;

import com.rrvgreenbasket.delivery.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ZoneNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleZoneNotFound(ZoneNotFoundException ex) {
        log.warn("Delivery zone not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidGeometryException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGeometry(InvalidGeometryException ex) {
        log.warn("Invalid geometry submitted: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ZoneOverlapException.class)
    public ResponseEntity<ApiResponse<Void>> handleZoneOverlap(ZoneOverlapException ex) {
        log.warn("Zone overlap conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidRequestException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidPlaceIdException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPlaceId(InvalidPlaceIdException ex) {
        log.warn("Invalid Place ID: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AddressVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAddressVerification(AddressVerificationException ex) {
        log.warn("Address verification failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidCoordinatesException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCoordinates(InvalidCoordinatesException ex) {
        log.warn("Invalid coordinates: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(CountryNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCountryNotSupported(CountryNotSupportedException ex) {
        log.warn("Country not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DeliveryNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryNotAvailable(DeliveryNotAvailableException ex) {
        log.warn("Delivery not available: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRouteNotFound(RouteNotFoundException ex) {
        log.warn("Route not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(GoogleApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleGoogleApiException(GoogleApiException ex) {
        log.error("Google Maps API error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("Mapping and routing service is temporarily unavailable. Please try again."));
    }

    @ExceptionHandler(GoogleApiQuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleGoogleApiQuotaExceeded(GoogleApiQuotaExceededException ex) {
        log.error("Google API quota exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("Location service request limit reached. Please try again shortly."));
    }

    @ExceptionHandler(DeliveryServiceDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryServiceDisabled(DeliveryServiceDisabledException ex) {
        log.warn("Delivery service is currently disabled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DeliveryPricingNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryPricingNotConfigured(DeliveryPricingNotConfiguredException ex) {
        log.error("Delivery pricing configuration error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(StoreLocationNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoreLocationNotConfigured(StoreLocationNotConfiguredException ex) {
        log.error("Store location configuration error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidDeliveryConfigurationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDeliveryConfiguration(InvalidDeliveryConfigurationException ex) {
        log.warn("Invalid delivery configuration: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Method argument validation failure: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(errorMessage));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Required parameter '" + ex.getParameterName() + "' is missing"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception in Delivery Service: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected internal error occurred. Please try again later."));
    }
}
