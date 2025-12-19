package vnd.credit.loans.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user tries to access a resource they don't own.
 * 
 * <p>This is used instead of generic {@code InternalServerError} to provide
 * proper HTTP semantics. Returns HTTP 403 Forbidden.</p>
 * 
 * <h3>HTTP 403 vs 401</h3>
 * <ul>
 *   <li>401 Unauthorized: User is not authenticated (not logged in)</li>
 *   <li>403 Forbidden: User is authenticated but not authorized for this resource</li>
 * </ul>
 * 
 * <h3>Original Code Problem</h3>
 * <pre>{@code
 * // Bad: Returns 500 for authorization failure
 * throw new InternalServerError();
 * }</pre>
 * 
 * <h3>Fixed Code</h3>
 * <pre>{@code
 * // Good: Returns 403 with clear message
 * throw new UnauthorizedAccessException("You are not authorized to access this account");
 * }</pre>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedAccessException extends RuntimeException {

    /**
     * Constructs a new UnauthorizedAccessException with the specified message.
     *
     * @param message the detail message explaining why access was denied
     */
    public UnauthorizedAccessException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnauthorizedAccessException with message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

