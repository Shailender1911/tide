package vnd.credit.loans.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an account has insufficient balance for a transaction.
 * 
 * <p>Returns HTTP 422 Unprocessable Entity - the request was valid but cannot
 * be processed due to business rule constraints.</p>
 * 
 * <h3>Why 422 and not 400?</h3>
 * <ul>
 *   <li>400 Bad Request: Syntactically invalid request (wrong format, missing fields)</li>
 *   <li>422 Unprocessable Entity: Syntactically valid but semantically invalid 
 *       (business rule violation)</li>
 * </ul>
 * 
 * <h3>Original Code Problem</h3>
 * <pre>{@code
 * // Bad: Returns 500 for a business rule violation
 * throw new InternalServerError();
 * }</pre>
 * 
 * <h3>Fixed Code</h3>
 * <pre>{@code
 * // Good: Returns 422 with clear message
 * throw new InsufficientBalanceException(
 *     String.format("Insufficient balance. Available: %s, Required: %s", balance, amount));
 * }</pre>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientBalanceException extends RuntimeException {

    /**
     * Constructs a new InsufficientBalanceException with the specified message.
     *
     * @param message the detail message explaining the balance shortage
     */
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

