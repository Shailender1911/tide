package vnd.credit.loans.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a loan request exceeds the user's credit limit.
 * 
 * <p>Returns HTTP 400 Bad Request - the request cannot be fulfilled because
 * the requested amount exceeds what the user is allowed to borrow.</p>
 * 
 * <h3>Original Code Problem</h3>
 * <p>The original code silently ignored requests that exceeded the credit limit
 * (no else branch, no error thrown). The client had no idea why the loan wasn't processed.</p>
 * 
 * <pre>{@code
 * // Bad: Silent failure - nothing happens if condition is false
 * if (loanAmount < loanManagementService.getCreditLimit(accountId)) {
 *     // process loan
 * }
 * // else: ??? client never knows what happened
 * }</pre>
 * 
 * <h3>Fixed Code</h3>
 * <pre>{@code
 * // Good: Explicit error with clear message
 * if (loanAmount.compareTo(creditLimit) > 0) {
 *     throw new CreditLimitExceededException(
 *         String.format("Loan amount %s exceeds credit limit %s", loanAmount, creditLimit));
 * }
 * }</pre>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CreditLimitExceededException extends RuntimeException {

    /**
     * Constructs a new CreditLimitExceededException with the specified message.
     *
     * @param message the detail message explaining the credit limit violation
     */
    public CreditLimitExceededException(String message) {
        super(message);
    }
}

