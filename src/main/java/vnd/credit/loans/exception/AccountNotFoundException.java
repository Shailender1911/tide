package vnd.credit.loans.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an account is not found in the system.
 * 
 * <p>Returns HTTP 404 Not Found instead of 500 Internal Server Error.</p>
 * 
 * <h3>Original Code Problem</h3>
 * <pre>{@code
 * // Bad: NoSuchElementException with no message
 * Optional.ofNullable(sourceAccount).orElseThrow();
 * }</pre>
 * 
 * <h3>Fixed Code</h3>
 * <pre>{@code
 * // Good: Specific exception with clear message
 * if (sourceAccount == null) {
 *     throw new AccountNotFoundException(sourceAccountId);
 * }
 * }</pre>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

    private final String accountId;

    /**
     * Constructs a new AccountNotFoundException for the specified account.
     *
     * @param accountId the ID of the account that was not found
     */
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
        this.accountId = accountId;
    }

    /**
     * Gets the account ID that was not found.
     *
     * @return the account ID
     */
    public String getAccountId() {
        return accountId;
    }
}

