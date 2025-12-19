package vnd.credit.loans.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the loan API.
 * 
 * <p>Provides centralized exception handling across all controllers,
 * ensuring consistent error response format.</p>
 * 
 * <h3>Benefits</h3>
 * <ul>
 *   <li>Consistent error response format across all endpoints</li>
 *   <li>Centralized logging of exceptions</li>
 *   <li>Prevents stack traces from being exposed to clients</li>
 *   <li>Maps exceptions to appropriate HTTP status codes</li>
 * </ul>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors from @Valid annotations.
     * 
     * <p>Returns HTTP 400 with details of which fields failed validation.</p>
     *
     * @param ex the validation exception
     * @return error response with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        logger.warn("Validation failed: {}", fieldErrors);

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                fieldErrors,
                Instant.now()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles unauthorized access exceptions.
     *
     * @param ex the exception
     * @return error response with 403 status
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        logger.warn("Unauthorized access: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                null,
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handles account not found exceptions.
     *
     * @param ex the exception
     * @return error response with 404 status
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        logger.warn("Account not found: {}", ex.getAccountId());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null,
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles insufficient balance exceptions.
     *
     * @param ex the exception
     * @return error response with 422 status
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        logger.warn("Insufficient balance: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                null,
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handles credit limit exceeded exceptions.
     *
     * @param ex the exception
     * @return error response with 400 status
     */
    @ExceptionHandler(CreditLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleCreditLimitExceeded(CreditLimitExceededException ex) {
        logger.warn("Credit limit exceeded: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                null,
                Instant.now()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles all other unexpected exceptions.
     * 
     * <p>This is a catch-all handler to prevent stack traces from
     * being exposed to clients.</p>
     *
     * @param ex the exception
     * @return error response with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Log the full exception for debugging
        logger.error("Unexpected error occurred", ex);

        // Return generic message to client (don't expose internals)
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please try again later.",
                null,
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Standard error response structure.
     */
    public static class ErrorResponse {
        private final int status;
        private final String message;
        private final Map<String, String> errors;
        private final Instant timestamp;

        public ErrorResponse(int status, String message, Map<String, String> errors, Instant timestamp) {
            this.status = status;
            this.message = message;
            this.errors = errors;
            this.timestamp = timestamp;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, String> getErrors() {
            return errors;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
}

