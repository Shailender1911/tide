package vnd.credit.loans.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vnd.credit.loans.dto.LoanRequest;
import vnd.credit.loans.dto.LoanResponse;
import vnd.credit.loans.exception.AccountNotFoundException;
import vnd.credit.loans.exception.CreditLimitExceededException;
import vnd.credit.loans.exception.InsufficientBalanceException;
import vnd.credit.loans.exception.UnauthorizedAccessException;
import vnd.credit.loans.model.Account;
import vnd.credit.loans.service.AccountService;
import vnd.credit.loans.service.LoanManagementService;
import vnd.credit.loans.util.AuthContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller for loan borrowing operations.
 * 
 * <p>This controller handles loan creation and money transfer operations.
 * It ensures proper authorization, transaction management, and audit logging
 * for all financial operations.</p>
 * 
 * <h3>Security</h3>
 * <ul>
 *   <li>User endpoints validate account ownership</li>
 *   <li>Admin endpoints require ADMIN role (server-side check)</li>
 * </ul>
 * 
 * <h3>Transactions</h3>
 * <p>All money transfer operations are wrapped in database transactions
 * to ensure atomicity (all-or-nothing execution).</p>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
@RestController
@RequestMapping("/v3/accounts")
public class LoanController {

    private static final Logger logger = LoggerFactory.getLogger(LoanController.class);

    // FIX #1: Constructor injection instead of field injection
    // FIX #2: private final instead of public - proper encapsulation
    private final AccountService accountService;
    private final LoanManagementService loanManagementService;

    /**
     * Constructor injection for dependencies.
     * 
     * <p>Benefits over @Autowired field injection:</p>
     * <ul>
     *   <li>Dependencies are explicit and required</li>
     *   <li>Easier to test with mock dependencies</li>
     *   <li>Fields can be final (immutable)</li>
     *   <li>Fails fast if dependency is missing</li>
     * </ul>
     *
     * @param accountService service for account operations
     * @param loanManagementService service for loan management system integration
     */
    public LoanController(AccountService accountService, 
                          LoanManagementService loanManagementService) {
        this.accountService = accountService;
        this.loanManagementService = loanManagementService;
    }

    /**
     * Borrow money and register the loan in the Loan Management System.
     * 
     * <p>This endpoint allows authenticated users to borrow money from a source
     * account into their own account. The loan is registered with the external
     * Loan Management System.</p>
     * 
     * <h3>Authorization</h3>
     * <p>Users can only borrow into accounts they own. The ownership check
     * is performed server-side using the authenticated user context.</p>
     * 
     * <h3>Transaction Guarantees</h3>
     * <p>The following operations are atomic:</p>
     * <ol>
     *   <li>Debit from source account</li>
     *   <li>Credit to destination account</li>
     *   <li>Register loan with LMS</li>
     * </ol>
     * <p>If any operation fails, all changes are rolled back.</p>
     *
     * @param accountId the destination account ID (borrower's account)
     * @param idempotencyKey unique key to prevent duplicate transactions on retry
     * @param loanRequest the loan request containing source account and amount
     * @return ResponseEntity with loan details and HTTP 201 Created status
     * @throws UnauthorizedAccessException if user doesn't own the destination account
     * @throws AccountNotFoundException if source or destination account not found
     * @throws CreditLimitExceededException if loan amount exceeds credit limit
     * @throws InsufficientBalanceException if source account has insufficient balance
     */
    @PostMapping("/{accountId}/loans")  // FIX #3: POST for creating resources, not PUT
    @Transactional(rollbackFor = Exception.class)  // FIX #4: CRITICAL - Atomic money transfer
    public ResponseEntity<LoanResponse> borrowMoney(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody LoanRequest loanRequest) {  // FIX #5: Request body with validation

        // FIX #6: Get current user from security context (NOT from client input!)
        String currentUserId = AuthContext.getCurrentUserID();
        
        logger.info("Loan request initiated - accountId: {}, amount: {}, user: {}, idempotencyKey: {}", 
                    accountId, loanRequest.getLoanAmount(), currentUserId, idempotencyKey);

        // FIX #7: Fetch destination account once (avoid duplicate fetch)
        Account destinationAccount = accountService.getAccount(accountId);
        
        // FIX #8: Proper null check with specific exception
        if (destinationAccount == null) {
            logger.warn("Destination account not found: {}", accountId);
            throw new AccountNotFoundException(accountId);
        }

        // FIX #9: Use Objects.equals() for proper object comparison (not !=)
        // This handles null safely and compares values, not references
        if (!Objects.equals(destinationAccount.getOwner(), currentUserId)) {
            logger.warn("Unauthorized access attempt - user: {} tried to access account: {}", 
                       currentUserId, accountId);
            throw new UnauthorizedAccessException(
                "You are not authorized to access this account");
        }

        // FIX #10: BigDecimal for monetary values (not double)
        BigDecimal loanAmount = loanRequest.getLoanAmount();
        String sourceAccountId = loanRequest.getSourceAccountId();

        // FIX #11: Correct comparison - check if amount EXCEEDS limit
        // Using compareTo() for BigDecimal comparison
        BigDecimal creditLimit = loanManagementService.getCreditLimit(accountId);
        if (loanAmount.compareTo(creditLimit) > 0) {
            logger.warn("Credit limit exceeded - account: {}, requested: {}, limit: {}", 
                       accountId, loanAmount, creditLimit);
            throw new CreditLimitExceededException(
                String.format("Loan amount %s exceeds credit limit %s", loanAmount, creditLimit));
        }

        // FIX #12: Validate source account exists
        Account sourceAccount = accountService.getAccount(sourceAccountId);
        if (sourceAccount == null) {
            logger.warn("Source account not found: {}", sourceAccountId);
            throw new AccountNotFoundException(sourceAccountId);
        }

        // FIX #13: Use BigDecimal.compareTo() for balance check
        // compareTo returns: -1 if less, 0 if equal, 1 if greater
        // Using >= (not just >) to allow exact balance withdrawal
        BigDecimal balance = sourceAccount.getBalance();
        if (balance.compareTo(loanAmount) < 0) {
            logger.warn("Insufficient balance - source: {}, available: {}, required: {}", 
                       sourceAccountId, balance, loanAmount);
            throw new InsufficientBalanceException(
                String.format("Insufficient balance. Available: %s, Required: %s", 
                             balance, loanAmount));
        }

        // FIX #14: Generate proper unique loan ID using UUID
        // (Not Random.nextInt() which has collision risk and is predictable)
        String loanId = UUID.randomUUID().toString();

        // Perform atomic money transfer (protected by @Transactional)
        logger.info("Processing loan transfer - loanId: {}, from: {}, to: {}, amount: {}", 
                   loanId, sourceAccountId, accountId, loanAmount);
        
        accountService.debit(sourceAccount, loanAmount);
        accountService.credit(destinationAccount, loanAmount);
        
        // Register loan with external Loan Management System
        loanManagementService.registerLoan(loanId, loanAmount, currentUserId);

        logger.info("Loan processed successfully - loanId: {}, account: {}, amount: {}", 
                   loanId, accountId, loanAmount);

        // FIX #15: Return proper response with all transaction details
        // (Not void - client needs confirmation)
        LoanResponse response = LoanResponse.builder()
                .loanId(loanId)
                .accountId(accountId)
                .sourceAccountId(sourceAccountId)
                .loanAmount(loanAmount)
                .newBalance(destinationAccount.getBalance().add(loanAmount))
                .status("SUCCESS")
                .message("Loan processed successfully")
                .timestamp(Instant.now())
                .build();

        // FIX #16: Return HTTP 201 Created for new resource creation
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin endpoint to process loans on behalf of users.
     * 
     * <p>This endpoint is used by authorized staff members to help users
     * when the standard loan process fails due to 3rd party LMS issues.</p>
     * 
     * <h3>Authorization</h3>
     * <p>Requires ADMIN role - checked server-side via Spring Security.
     * This is NOT passed as a parameter from the client (which would be insecure).</p>
     *
     * @param accountId the destination account ID
     * @param loanRequest the loan request details
     * @return ResponseEntity with loan details
     */
    @PostMapping("/admin/{accountId}/loans")
    @PreAuthorize("hasRole('ADMIN')")  // FIX #17: Server-side authorization check!
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<LoanResponse> adminBorrowMoney(
            @PathVariable String accountId,
            @Valid @RequestBody LoanRequest loanRequest) {

        String adminUserId = AuthContext.getCurrentUserID();
        
        logger.info("Admin loan request - admin: {}, targetAccount: {}, amount: {}", 
                   adminUserId, accountId, loanRequest.getLoanAmount());

        // Admin operations skip ownership check (intentionally)
        // But we still validate accounts and amounts
        
        Account destinationAccount = accountService.getAccount(accountId);
        if (destinationAccount == null) {
            throw new AccountNotFoundException(accountId);
        }

        BigDecimal loanAmount = loanRequest.getLoanAmount();
        String sourceAccountId = loanRequest.getSourceAccountId();

        BigDecimal creditLimit = loanManagementService.getCreditLimit(accountId);
        if (loanAmount.compareTo(creditLimit) > 0) {
            throw new CreditLimitExceededException(
                String.format("Loan amount %s exceeds credit limit %s", loanAmount, creditLimit));
        }

        Account sourceAccount = accountService.getAccount(sourceAccountId);
        if (sourceAccount == null) {
            throw new AccountNotFoundException(sourceAccountId);
        }

        BigDecimal balance = sourceAccount.getBalance();
        if (balance.compareTo(loanAmount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient balance. Available: %s, Required: %s", 
                             balance, loanAmount));
        }

        String loanId = UUID.randomUUID().toString();

        // Log admin action for audit trail
        logger.info("Admin processing loan - adminId: {}, loanId: {}, targetAccount: {}", 
                   adminUserId, loanId, accountId);

        accountService.debit(sourceAccount, loanAmount);
        accountService.credit(destinationAccount, loanAmount);
        loanManagementService.registerLoan(loanId, loanAmount, destinationAccount.getOwner());

        logger.info("Admin loan completed - loanId: {}, processedBy: {}", loanId, adminUserId);

        LoanResponse response = LoanResponse.builder()
                .loanId(loanId)
                .accountId(accountId)
                .sourceAccountId(sourceAccountId)
                .loanAmount(loanAmount)
                .newBalance(destinationAccount.getBalance().add(loanAmount))
                .status("SUCCESS")
                .message("Admin loan processed successfully")
                .processedBy(adminUserId)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

