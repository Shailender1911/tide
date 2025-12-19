package vnd.credit.loans.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request DTO for loan borrowing operation.
 * 
 * <p>This DTO encapsulates all the data needed to process a loan request.
 * Using a request body DTO instead of query parameters provides:</p>
 * <ul>
 *   <li>Better structure for complex data</li>
 *   <li>Centralized validation with Bean Validation annotations</li>
 *   <li>Cleaner API signature</li>
 *   <li>Easier versioning and evolution</li>
 * </ul>
 * 
 * <h3>Validation</h3>
 * <p>All fields are validated using Jakarta Bean Validation annotations.
 * Invalid requests will result in HTTP 400 Bad Request.</p>
 * 
 * <h3>Money Precision</h3>
 * <p>Uses {@link BigDecimal} for loan amount to avoid floating-point
 * precision errors that occur with {@code double}.</p>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
public class LoanRequest {

    /**
     * The source account ID from which money will be borrowed.
     * 
     * <p>This must be a valid account ID in the system. The account
     * must have sufficient balance for the requested loan amount.</p>
     */
    @NotBlank(message = "Source account ID is required")
    private String sourceAccountId;

    /**
     * The amount of money to borrow.
     * 
     * <p>Must be a positive value greater than zero.
     * Uses BigDecimal for precise financial calculations.</p>
     * 
     * <p>Example valid values: 100.00, 1500.50, 0.01</p>
     */
    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "0.01", message = "Loan amount must be greater than zero")
    private BigDecimal loanAmount;

    /**
     * Default constructor for JSON deserialization.
     */
    public LoanRequest() {
    }

    /**
     * Constructs a new LoanRequest with the specified values.
     *
     * @param sourceAccountId the source account ID
     * @param loanAmount the loan amount
     */
    public LoanRequest(String sourceAccountId, BigDecimal loanAmount) {
        this.sourceAccountId = sourceAccountId;
        this.loanAmount = loanAmount;
    }

    // Getters and Setters

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(String sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(BigDecimal loanAmount) {
        this.loanAmount = loanAmount;
    }

    @Override
    public String toString() {
        return "LoanRequest{" +
                "sourceAccountId='" + sourceAccountId + '\'' +
                ", loanAmount=" + loanAmount +
                '}';
    }
}

