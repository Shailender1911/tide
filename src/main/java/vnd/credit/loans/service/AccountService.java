package vnd.credit.loans.service;

import vnd.credit.loans.model.Account;
import java.math.BigDecimal;

/**
 * Service interface for account operations.
 * 
 * <p>All monetary operations use {@link BigDecimal} to ensure precision.</p>
 * 
 * <h3>Implementation Note</h3>
 * <p>Implementations should ensure thread-safety and proper transaction
 * handling for concurrent access.</p>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
public interface AccountService {

    /**
     * Retrieves an account by its ID.
     *
     * @param accountId the account ID
     * @return the account, or null if not found
     */
    Account getAccount(String accountId);

    /**
     * Debits (withdraws) money from an account.
     * 
     * <p>This operation should be called within a transaction to ensure
     * atomicity with corresponding credit operations.</p>
     *
     * @param account the account to debit
     * @param amount the amount to debit (must be positive)
     * @throws IllegalArgumentException if amount is negative or zero
     * @throws IllegalStateException if account has insufficient balance
     */
    void debit(Account account, BigDecimal amount);

    /**
     * Credits (deposits) money to an account.
     * 
     * <p>This operation should be called within a transaction to ensure
     * atomicity with corresponding debit operations.</p>
     *
     * @param account the account to credit
     * @param amount the amount to credit (must be positive)
     * @throws IllegalArgumentException if amount is negative or zero
     */
    void credit(Account account, BigDecimal amount);

    /**
     * Checks if an account exists.
     *
     * @param accountId the account ID
     * @return true if account exists, false otherwise
     */
    boolean exists(String accountId);
}

