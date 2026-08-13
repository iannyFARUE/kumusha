package com.kumusha.exception;

import com.kumusha.model.response.ErrorResponse;
import com.mongodb.MongoWriteException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for the application.
 *
 * <p>This class uses @ControllerAdvice to handle exceptions thrown by controllers
 * and convert them into appropriate HTTP responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        logger.error("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(ex.getMessage())
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("RESOURCE_NOT_FOUND")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(WriteOperationsDisabledException.class)
    public ResponseEntity<ErrorResponse> handleWriteOperationsDisabledException(
            WriteOperationsDisabledException ex, WebRequest request) {
        // Logged at the point of rejection by WriteGuardInterceptor, which knows the method and
        // path, so this handler only shapes the response.
        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(ex.getMessage())
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("WRITE_OPERATIONS_DISABLED")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, WebRequest request) {
        logger.error("Validation error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message("Validation failed")
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("VALIDATION_ERROR")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles bean validation failures on {@code @Valid} request bodies.
     *
     * <p>Without this handler a malformed request body would fall through to the catch-all
     * below and be reported as a 500, hiding a plain client mistake behind a server error.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        String message = details.isEmpty() ? "Request body is invalid" : details;

        logger.error("Validation error: {}", message);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message("Validation failed")
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(message)
                        .code("VALIDATION_ERROR")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, WebRequest request) {
        logger.error("Missing request parameter: {}", ex.getMessage());

        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(message)
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(message)
                        .code("VALIDATION_ERROR")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailableException(
            ServiceUnavailableException ex, WebRequest request) {
        logger.error("Service unavailable: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(ex.getMessage())
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("SERVICE_UNAVAILABLE")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(VoyageAuthException.class)
    public ResponseEntity<ErrorResponse> handleVoyageAuthException(
            VoyageAuthException ex, WebRequest request) {
        logger.error("Voyage AI authentication error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(ex.getMessage())
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("VOYAGE_AUTH_ERROR")
                        .details("Please verify your VOYAGE_API_KEY is correct in the .env file")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(VoyageAPIException.class)
    public ResponseEntity<ErrorResponse> handleVoyageAPIException(
            VoyageAPIException ex, WebRequest request) {
        logger.error("Voyage AI API error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message("Vector search service unavailable")
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("VOYAGE_API_ERROR")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(DatabaseOperationException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseOperationException(
            DatabaseOperationException ex, WebRequest request) {
        logger.error("Database operation error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message("Database operation failed")
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage())
                        .code("DATABASE_OPERATION_ERROR")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MongoWriteException.class)
    public ResponseEntity<ErrorResponse> handleMongoWriteException(
            MongoWriteException ex, WebRequest request) {
        logger.error("MongoDB write error: {}", ex.getMessage());

        String message = "Database error";
        String code = "DATABASE_ERROR";
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (ex.getError().getCode() == 11000) {
            message = "Duplicate key error";
            code = "DUPLICATE_KEY";
            status = HttpStatus.CONFLICT;
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(message)
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(message)
                        .code(code)
                        .details(ex.getError().getCode())
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        logger.error("Unexpected error occurred", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(ex.getMessage() != null ? ex.getMessage() : "Internal server error")
                .error(ErrorResponse.ErrorDetails.builder()
                        .message(ex.getMessage() != null ? ex.getMessage() : "Internal server error")
                        .code("INTERNAL_ERROR")
                        .build())
                .timestamp(Instant.now().toString())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
