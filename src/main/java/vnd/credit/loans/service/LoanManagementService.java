package vnd.credit.loans.service;

import java.math.BigDecimal;

/**
 * Service interface for Loan Management System (LMS) operations.
 * 
 * <p>This service wraps the external 3rd party LMS which is accessed
 * via a Feign client. The external service is prone to failures,
 * so implementations should handle retries and circuit breaking.</p>
 * 
 * <h3>Error Handling Strategy</h3>
 * <p>Since the external LMS can fail, consider implementing:</p>
 * <ul>
 *   <li>Retry with exponential backoff</li>
 *   <li>Circuit breaker pattern (e.g., Resilience4j)</li>
 *   <li>Transactional outbox for eventual consistency</li>
 *   <li>Compensating transactions for rollback</li>
 * </ul>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
public interface LoanManagementService {

    /**
     * Gets the credit limit for an account.
     * 
     * <p>The credit limit determines the maximum amount a user
     * can borrow.</p>
     *
     * @param accountId the account ID
     * @return the credit limit as BigDecimal
     */
    BigDecimal getCreditLimit(String accountId);

    /**
     * Registers a new loan in the external Loan Management System.
     * 
     * <p>This method should be called after the money transfer is complete.
     * Uses UUID for loanId (not random integers) to ensure uniqueness.</p>
     * 
     * <h3>Original Code Problem</h3>
     * <pre>{@code
     * // BAD: Random integers can collide, not unique
     * loanManagementService.registerLoan(new Random().nextInt(1000000), ...);
     * }</pre>
     * 
     * <h3>Fixed Code</h3>
     * <pre>{@code
     * // GOOD: UUID is universally unique
     * String loanId = UUID.randomUUID().toString();
     * loanManagementService.registerLoan(loanId, amount, userId);
     * }</pre>
     *
     * @param loanId unique identifier for the loan (UUID)
     * @param amount the loan amount
     * @param userId the user who took the loan
     * @throws LoanRegistrationException if registration fails
     */
    void registerLoan(String loanId, BigDecimal amount, String userId);

    /**
     * Gets loan details from the LMS.
     *
     * @param loanId the loan ID
     * @return loan details, or null if not found
     */
    LoanDetails getLoan(String loanId);

    /**
     * DTO for loan details.
     */
    record LoanDetails(
            String loanId,
            BigDecimal amount,
            String userId,
            String status
    ) {}
}

