package vnd.credit.loans.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO containing loan transaction details.
 * 
 * <p>This DTO provides comprehensive information about a completed loan
 * transaction, allowing clients to:</p>
 * <ul>
 *   <li>Confirm the transaction was successful</li>
 *   <li>Reference the loan by its unique ID</li>
 *   <li>Track the transaction timestamp</li>
 *   <li>Verify the amounts and accounts involved</li>
 * </ul>
 * 
 * <h3>Why Return a Response?</h3>
 * <p>The original code returned {@code void}, which is problematic because:</p>
 * <ul>
 *   <li>Clients don't know if the operation succeeded</li>
 *   <li>No loan reference ID for future queries</li>
 *   <li>No audit trail confirmation</li>
 *   <li>Violates REST best practices (201 Created should have body)</li>
 * </ul>
 * 
 * <h3>Builder Pattern</h3>
 * <p>Uses builder pattern for cleaner object construction with many fields.</p>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // Don't serialize null fields
public class LoanResponse {

    /**
     * Unique identifier for the loan transaction.
     * Generated using UUID to ensure uniqueness.
     */
    private String loanId;

    /**
     * The destination account ID (borrower's account).
     */
    private String accountId;

    /**
     * The source account ID from which money was borrowed.
     */
    private String sourceAccountId;

    /**
     * The amount that was borrowed.
     */
    private BigDecimal loanAmount;

    /**
     * The new balance in the destination account after the loan.
     */
    private BigDecimal newBalance;

    /**
     * The status of the transaction (e.g., "SUCCESS", "PENDING").
     */
    private String status;

    /**
     * Human-readable message about the transaction.
     */
    private String message;

    /**
     * Timestamp when the transaction was processed.
     */
    private Instant timestamp;

    /**
     * Admin user ID if processed by admin (optional).
     */
    private String processedBy;

    // Private constructor for builder
    private LoanResponse() {
    }

    /**
     * Creates a new builder for LoanResponse.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing LoanResponse instances.
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * LoanResponse response = LoanResponse.builder()
     *     .loanId("uuid-here")
     *     .accountId("acc-123")
     *     .loanAmount(new BigDecimal("1000.00"))
     *     .status("SUCCESS")
     *     .timestamp(Instant.now())
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private final LoanResponse response = new LoanResponse();

        public Builder loanId(String loanId) {
            response.loanId = loanId;
            return this;
        }

        public Builder accountId(String accountId) {
            response.accountId = accountId;
            return this;
        }

        public Builder sourceAccountId(String sourceAccountId) {
            response.sourceAccountId = sourceAccountId;
            return this;
        }

        public Builder loanAmount(BigDecimal loanAmount) {
            response.loanAmount = loanAmount;
            return this;
        }

        public Builder newBalance(BigDecimal newBalance) {
            response.newBalance = newBalance;
            return this;
        }

        public Builder status(String status) {
            response.status = status;
            return this;
        }

        public Builder message(String message) {
            response.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            response.timestamp = timestamp;
            return this;
        }

        public Builder processedBy(String processedBy) {
            response.processedBy = processedBy;
            return this;
        }

        public LoanResponse build() {
            return response;
        }
    }

    // Getters (no setters - immutable after construction)

    public String getLoanId() {
        return loanId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    @Override
    public String toString() {
        return "LoanResponse{" +
                "loanId='" + loanId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", sourceAccountId='" + sourceAccountId + '\'' +
                ", loanAmount=" + loanAmount +
                ", newBalance=" + newBalance +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", processedBy='" + processedBy + '\'' +
                '}';
    }
}

