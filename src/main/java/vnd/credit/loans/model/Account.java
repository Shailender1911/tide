package vnd.credit.loans.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity representing a bank account.
 * 
 * <p>Key design decisions:</p>
 * <ul>
 *   <li>Uses {@link BigDecimal} for balance to avoid floating-point precision issues</li>
 *   <li>Includes version field for optimistic locking (prevents race conditions)</li>
 *   <li>Immutable ID and creation timestamp</li>
 * </ul>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The owner's user ID.
     * Used for authorization checks to ensure users can only access their own accounts.
     */
    @Column(nullable = false)
    private String owner;

    /**
     * Account balance using BigDecimal for precision.
     * 
     * <p>NEVER use double for money:</p>
     * <pre>{@code
     * // BAD: double has precision issues
     * double balance = 100.10;
     * 
     * // GOOD: BigDecimal is exact
     * BigDecimal balance = new BigDecimal("100.10");
     * }</pre>
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Version field for optimistic locking.
     * 
     * <p>Prevents race conditions when multiple transactions try to
     * modify the same account simultaneously.</p>
     * 
     * <p>If two transactions read the same version and try to update,
     * only one will succeed; the other will get an OptimisticLockException
     * and can retry.</p>
     */
    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Account status (ACTIVE, FROZEN, CLOSED).
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ACTIVE;

    // Lifecycle callbacks
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Constructors
    public Account() {
    }

    public Account(String owner, BigDecimal balance) {
        this.owner = owner;
        this.balance = balance;
        this.status = AccountStatus.ACTIVE;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /**
     * Account status enumeration.
     */
    public enum AccountStatus {
        ACTIVE,
        FROZEN,
        CLOSED
    }

    @Override
    public String toString() {
        return "Account{" +
                "id='" + id + '\'' +
                ", owner='" + owner + '\'' +
                ", balance=" + balance +
                ", status=" + status +
                '}';
    }
}

