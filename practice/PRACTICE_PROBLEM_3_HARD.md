# Practice Problem 3: Money Transfer API (Hard - Similar to Tide Interview)

## Context
A junior developer has written a money transfer API for a banking application. This is very similar to the actual Tide interview problem. Review this code and identify ALL issues.

**Time Limit**: 40 minutes  
**Difficulty**: Hard  
**Expected Issues**: 20+

---

## Code to Review

```java
package com.bank.transfers;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Transfer API v2 - Added batch transfers and staff override for
 * when customers call support complaining about failed transfers.
 * Also added caching for better performance!
 */
@RequestMapping("/api/v2/transfers")
@RestController
public class TransferController {

    @Autowired
    public AccountRepository accountRepository;

    @Autowired
    public TransferService transferService;

    @Autowired
    public AuditService auditService;

    @Autowired
    public FraudDetectionService fraudService;

    public static final Logger logger = LoggerFactory.getLogger(TransferController.class);

    // Cache for account balances
    private static ConcurrentHashMap<String, Double> balanceCache = new ConcurrentHashMap<>();

    @PutMapping("/send")
    public void sendMoney(@RequestParam String fromAccount,
                          @RequestParam String toAccount,
                          @RequestParam double amount,
                          @RequestParam String description,
                          @RequestParam boolean staffOverride) {

        if (!staffOverride) {
            Account from = accountRepository.findById(fromAccount).get();
            if (from.getOwnerId() != SecurityContext.getUserId()) {
                throw new Exception("Unauthorized");
            }
        }

        // Check fraud
        if (fraudService.isSuspicious(fromAccount, amount) && !staffOverride) {
            throw new InternalServerError();
        }

        // Get or cache balances for performance
        Double fromBalance = balanceCache.computeIfAbsent(fromAccount, 
            id -> accountRepository.findById(id).get().getBalance());
        
        if (fromBalance > amount) {
            
            Account from = accountRepository.findById(fromAccount).get();
            Account to = accountRepository.findById(toAccount).get();

            from.setBalance(from.getBalance() - amount);
            to.setBalance(to.getBalance() + amount);

            accountRepository.save(from);
            accountRepository.save(to);

            // Update cache
            balanceCache.put(fromAccount, from.getBalance());
            balanceCache.put(toAccount, to.getBalance());

            // Create transfer record
            Transfer transfer = new Transfer();
            transfer.setId(System.currentTimeMillis() + "" + new Random().nextInt(1000));
            transfer.setFromAccount(fromAccount);
            transfer.setToAccount(toAccount);
            transfer.setAmount(amount);
            transfer.setStatus("COMPLETED");
            transferService.save(transfer);

            auditService.log("Transfer completed: " + amount);

        } else {
            throw new InternalServerError();
        }
    }

    @PostMapping("/batch")
    public void batchTransfer(@RequestBody List<TransferRequest> transfers,
                              @RequestParam boolean skipValidation) {

        for (TransferRequest req : transfers) {
            try {
                sendMoney(req.getFrom(), req.getTo(), req.getAmount(), 
                         req.getDescription(), skipValidation);
            } catch (Exception e) {
                // Continue with next transfer
                continue;
            }
        }
    }

    @GetMapping("/balance/{accountId}")
    public Double getBalance(@PathVariable String accountId) {
        return balanceCache.getOrDefault(accountId, 
            accountRepository.findById(accountId).get().getBalance());
    }
}
```

---

## Your Task

1. Identify ALL bugs and issues (aim for 20+)
2. Categorize them by severity (CRITICAL, MAJOR, MINOR)
3. Suggest fixes for each issue
4. Think about: Security, Transactions, Concurrency, Money handling, API design

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Authorization bypass (staffOverride)
- Transaction atomicity (what if one save fails?)
- Cache consistency issues
- Race conditions with concurrent transfers
- Money precision (double)
- ID generation for transfers
- Balance comparison operators
- Response bodies
- HTTP methods
- Error handling and exceptions
- Null safety (Optional.get())
- Batch processing errors
- Static mutable state
- skipValidation parameter

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Security Issues

1. **staffOverride from client input** (Lines 38, 40, 47)
   ```java
   @RequestParam boolean staffOverride
   if (!staffOverride) { ... }
   // CRITICAL: Anyone can bypass all security by setting staffOverride=true
   // Fix: Use @PreAuthorize("hasRole('STAFF')") - server-side role check
   ```

2. **skipValidation for batch transfers** (Lines 83, 86)
   ```java
   @RequestParam boolean skipValidation
   sendMoney(..., skipValidation);
   // CRITICAL: Allows bypassing fraud detection and auth for batch transfers!
   // Fix: Never allow validation skip from client input
   ```

3. **Fraud check can be bypassed** (Line 47)
   ```java
   if (fraudService.isSuspicious(...) && !staffOverride)
   // Fraud detection bypassed with staffOverride=true
   ```

### 🔴 CRITICAL Data Integrity Issues

4. **No @Transactional - Transfer NOT atomic** (Lines 56-62)
   ```java
   from.setBalance(from.getBalance() - amount);
   to.setBalance(to.getBalance() + amount);
   accountRepository.save(from);
   accountRepository.save(to);  // What if this fails?
   // Money disappears! Debit succeeded but credit failed
   // Fix: Add @Transactional(rollbackFor = Exception.class)
   ```

5. **Cache inconsistency - SEVERE** (Lines 31, 51-53, 63-64)
   ```java
   private static ConcurrentHashMap<String, Double> balanceCache
   Double fromBalance = balanceCache.computeIfAbsent(...)
   // Cache may have stale data, allowing overdrafts!
   // Balance shown may not match actual balance
   // Race condition: two threads read same cached balance
   // Fix: Don't cache balances, or use proper cache invalidation
   ```

6. **double for money** (Lines 36, 51, etc.)
   ```java
   @RequestParam double amount
   Double fromBalance
   // Precision errors: 0.1 + 0.2 != 0.3
   // Fix: Use BigDecimal for all money calculations
   ```

7. **Race condition in balance check** (Lines 51-62)
   ```java
   if (fromBalance > amount) {
       // Another thread could transfer money here
       from.setBalance(from.getBalance() - amount);
   }
   // Fix: Use optimistic locking (version field) or SELECT FOR UPDATE
   ```

### 🔴 MAJOR Issues

8. **No response body** (Lines 33, 82)
   ```java
   public void sendMoney(...)
   public void batchTransfer(...)
   // Client gets no confirmation, transfer ID, receipt
   // Fix: Return ResponseEntity<TransferResponse>
   ```

9. **Wrong HTTP method** (Line 33)
   ```java
   @PutMapping("/send")  // Should be POST (creating a new transfer)
   ```

10. **Unsafe Optional.get()** (Lines 41, 52, 55, 56, 96)
    ```java
    accountRepository.findById(fromAccount).get()
    // Throws NoSuchElementException if account not found
    // Fix: Use orElseThrow(() -> new AccountNotFoundException(...))
    ```

11. **Wrong comparison operators** (Lines 42, 52)
    ```java
    if (from.getOwnerId() != SecurityContext.getUserId())  // Use .equals()
    if (fromBalance > amount)  // Should be >= to allow exact balance transfer
    ```

12. **Transfer ID generation** (Line 68)
    ```java
    transfer.setId(System.currentTimeMillis() + "" + new Random().nextInt(1000));
    // Collision risk, not unique, predictable
    // Fix: Use UUID.randomUUID().toString()
    ```

13. **Batch silently ignores failures** (Lines 87-91)
    ```java
    } catch (Exception e) {
        continue;  // Failed transfers are silently ignored!
    }
    // Fix: Collect results, return success/failure for each transfer
    ```

14. **InternalServerError for business errors** (Lines 48, 77)
    ```java
    throw new InternalServerError();
    // Should be: 403 for fraud, 400 for insufficient balance
    ```

15. **Checked Exception from controller** (Line 43)
    ```java
    throw new Exception("Unauthorized");
    // Controllers shouldn't throw checked exceptions
    // Fix: throw new UnauthorizedException("...")
    ```

### 🟡 MEDIUM Issues

16. **Static mutable state** (Line 31)
    ```java
    private static ConcurrentHashMap<String, Double> balanceCache
    // Static state shared across all instances - problematic in distributed systems
    // Fix: Use proper distributed cache (Redis) or remove caching
    ```

17. **Field injection** (Lines 16-26)
    ```java
    @Autowired public AccountRepository
    // Should be constructor injection with private final fields
    ```

18. **Logger not used for transfers**
    - No logging of transfer attempts, amounts, accounts
    - Only generic "Transfer completed" logged
    - Need audit trail with transfer IDs

19. **No input validation**
    - No check for amount > 0
    - No check for fromAccount != toAccount
    - No validation on description length

20. **Account fetched multiple times** (Lines 41, 55-56)
    ```java
    Account from = accountRepository.findById(fromAccount).get();  // First fetch
    ...
    Account from = accountRepository.findById(fromAccount).get();  // Fetched again!
    // Inefficient and could return different data
    ```

21. **getBalance exposes raw balance** (Lines 94-97)
    ```java
    @GetMapping("/balance/{accountId}")
    public Double getBalance(@PathVariable String accountId)
    // No authorization check - anyone can see anyone's balance!
    ```

### 🟢 MINOR Issues

22. **Transfer record saved after money moved** (Lines 67-74)
    - If transfer save fails, money is moved but no record exists
    - Should be part of the same transaction

23. **Audit log has no details** (Line 76)
    ```java
    auditService.log("Transfer completed: " + amount);
    // Missing: from, to, transfer ID, user who initiated
    ```

24. **No idempotency key**
    - Retry could cause duplicate transfers

25. **Batch has no transaction boundary**
    - Should all succeed or all fail, or have clear partial success handling

### Summary

| Severity | Count |
|----------|-------|
| 🔴 CRITICAL | 7 |
| 🔴 MAJOR | 8 |
| 🟡 MEDIUM | 6 |
| 🟢 MINOR | 4 |
| **Total** | **25** |

</details>

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation (MOST IMPORTANT - similar to Tide!)</summary>

### Fixed Transfer Controller

```java
package com.bank.transfers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transfer API for money transfers between accounts.
 * 
 * CRITICAL: All money operations are atomic via @Transactional.
 * Uses BigDecimal for precision and proper authorization checks.
 */
@RestController
@RequestMapping("/api/v2/transfers")
public class TransferController {

    private static final Logger logger = LoggerFactory.getLogger(TransferController.class);

    // FIX: Constructor injection with private final
    private final AccountRepository accountRepository;
    private final TransferService transferService;
    private final AuditService auditService;
    private final FraudDetectionService fraudService;

    public TransferController(AccountRepository accountRepository,
                             TransferService transferService,
                             AuditService auditService,
                             FraudDetectionService fraudService) {
        this.accountRepository = accountRepository;
        this.transferService = transferService;
        this.auditService = auditService;
        this.fraudService = fraudService;
    }

    /**
     * Transfer money between accounts.
     */
    @PostMapping("/send")  // FIX: POST for creating transfer
    @Transactional(rollbackFor = Exception.class)  // FIX: CRITICAL - Atomic operation
    public ResponseEntity<TransferResponse> sendMoney(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        String currentUserId = SecurityContext.getUserId();
        
        logger.info("Transfer request - from: {}, to: {}, amount: {}, user: {}, idempotencyKey: {}", 
                   request.getFromAccount(), request.getToAccount(), 
                   request.getAmount(), currentUserId, idempotencyKey);

        // FIX: Validate accounts exist with proper error handling
        Account fromAccount = accountRepository.findById(request.getFromAccount())
            .orElseThrow(() -> new AccountNotFoundException(request.getFromAccount()));
        
        Account toAccount = accountRepository.findById(request.getToAccount())
            .orElseThrow(() -> new AccountNotFoundException(request.getToAccount()));

        // FIX: Server-side authorization check (NOT from client!)
        if (!Objects.equals(fromAccount.getOwnerId(), currentUserId)) {
            logger.warn("Unauthorized transfer attempt - user: {}, account: {}", 
                       currentUserId, request.getFromAccount());
            throw new UnauthorizedException("Not authorized to transfer from this account");
        }

        // FIX: Fraud check (no bypass from client)
        if (fraudService.isSuspicious(request.getFromAccount(), request.getAmount())) {
            logger.warn("Suspicious transfer blocked - account: {}, amount: {}", 
                       request.getFromAccount(), request.getAmount());
            throw new FraudDetectedException("Transfer flagged for review");
        }

        // FIX: Use BigDecimal.compareTo() for balance check (>= not just >)
        BigDecimal fromBalance = fromAccount.getBalance();
        if (fromBalance.compareTo(request.getAmount()) < 0) {
            logger.warn("Insufficient balance - account: {}, balance: {}, requested: {}", 
                       request.getFromAccount(), fromBalance, request.getAmount());
            throw new InsufficientBalanceException(
                "Insufficient balance. Available: " + fromBalance);
        }

        // Generate unique transfer ID
        String transferId = UUID.randomUUID().toString();

        // Perform atomic transfer
        fromAccount.setBalance(fromBalance.subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Create transfer record (same transaction)
        Transfer transfer = new Transfer();
        transfer.setId(transferId);
        transfer.setFromAccount(request.getFromAccount());
        transfer.setToAccount(request.getToAccount());
        transfer.setAmount(request.getAmount());
        transfer.setDescription(request.getDescription());
        transfer.setStatus("COMPLETED");
        transfer.setCreatedAt(Instant.now());
        transfer.setCreatedBy(currentUserId);
        transferService.save(transfer);

        // FIX: Detailed audit logging
        auditService.log(AuditEntry.builder()
            .action("TRANSFER")
            .transferId(transferId)
            .fromAccount(request.getFromAccount())
            .toAccount(request.getToAccount())
            .amount(request.getAmount())
            .userId(currentUserId)
            .timestamp(Instant.now())
            .build());

        logger.info("Transfer successful - transferId: {}, from: {}, to: {}, amount: {}", 
                   transferId, request.getFromAccount(), request.getToAccount(), request.getAmount());

        // FIX: Return proper response
        TransferResponse response = TransferResponse.builder()
            .transferId(transferId)
            .fromAccount(request.getFromAccount())
            .toAccount(request.getToAccount())
            .amount(request.getAmount())
            .status("COMPLETED")
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin endpoint for staff-assisted transfers.
     * ONLY accessible by users with STAFF role.
     */
    @PostMapping("/admin/send")
    @PreAuthorize("hasRole('STAFF')")  // FIX: Server-side role check!
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<TransferResponse> staffTransfer(
            @Valid @RequestBody TransferRequest request) {

        String staffUserId = SecurityContext.getUserId();
        
        logger.info("Staff transfer - staff: {}, from: {}, to: {}, amount: {}", 
                   staffUserId, request.getFromAccount(), request.getToAccount(), request.getAmount());

        // Validate accounts
        Account fromAccount = accountRepository.findById(request.getFromAccount())
            .orElseThrow(() -> new AccountNotFoundException(request.getFromAccount()));
        
        Account toAccount = accountRepository.findById(request.getToAccount())
            .orElseThrow(() -> new AccountNotFoundException(request.getToAccount()));

        // Staff can skip ownership check but NOT fraud check
        if (fraudService.isSuspicious(request.getFromAccount(), request.getAmount())) {
            throw new FraudDetectedException("Transfer requires manager approval");
        }

        BigDecimal fromBalance = fromAccount.getBalance();
        if (fromBalance.compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        String transferId = UUID.randomUUID().toString();

        fromAccount.setBalance(fromBalance.subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transfer transfer = new Transfer();
        transfer.setId(transferId);
        transfer.setFromAccount(request.getFromAccount());
        transfer.setToAccount(request.getToAccount());
        transfer.setAmount(request.getAmount());
        transfer.setStatus("COMPLETED");
        transfer.setCreatedAt(Instant.now());
        transfer.setCreatedBy(staffUserId);
        transfer.setStaffAssisted(true);  // Mark as staff-assisted
        transferService.save(transfer);

        // Enhanced audit for staff actions
        auditService.log(AuditEntry.builder()
            .action("STAFF_TRANSFER")
            .transferId(transferId)
            .staffId(staffUserId)
            .fromAccount(request.getFromAccount())
            .toAccount(request.getToAccount())
            .amount(request.getAmount())
            .timestamp(Instant.now())
            .build());

        logger.info("Staff transfer successful - transferId: {}, staff: {}", transferId, staffUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            TransferResponse.builder()
                .transferId(transferId)
                .status("COMPLETED")
                .timestamp(Instant.now())
                .build()
        );
    }

    /**
     * Get account balance.
     */
    @GetMapping("/balance/{accountId}")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        String currentUserId = SecurityContext.getUserId();

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));

        // FIX: Authorization check
        if (!Objects.equals(account.getOwnerId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized to view this account");
        }

        return ResponseEntity.ok(new BalanceResponse(
            accountId,
            account.getBalance(),
            Instant.now()
        ));
    }

    /**
     * Batch transfer with proper transaction handling.
     */
    @PostMapping("/batch")
    @Transactional(rollbackFor = Exception.class)  // All or nothing
    public ResponseEntity<BatchTransferResponse> batchTransfer(
            @Valid @RequestBody List<TransferRequest> requests) {

        String currentUserId = SecurityContext.getUserId();
        List<TransferResult> results = new ArrayList<>();

        // FIX: Validate ALL first before processing any
        for (TransferRequest req : requests) {
            validateTransferRequest(req, currentUserId);
        }

        // Process all (will all succeed or all rollback)
        for (TransferRequest req : requests) {
            TransferResult result = processTransfer(req, currentUserId);
            results.add(result);
        }

        return ResponseEntity.ok(new BatchTransferResponse(results, "ALL_COMPLETED"));
    }

    private void validateTransferRequest(TransferRequest req, String userId) {
        Account from = accountRepository.findById(req.getFromAccount())
            .orElseThrow(() -> new AccountNotFoundException(req.getFromAccount()));

        if (!Objects.equals(from.getOwnerId(), userId)) {
            throw new UnauthorizedException("Not authorized");
        }

        if (from.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for: " + req.getFromAccount());
        }
    }

    private TransferResult processTransfer(TransferRequest req, String userId) {
        // Implementation similar to sendMoney but returns result object
        return new TransferResult(UUID.randomUUID().toString(), "COMPLETED");
    }
}
```

### DTOs

```java
// Transfer Request
public class TransferRequest {
    @NotBlank(message = "Source account is required")
    private String fromAccount;

    @NotBlank(message = "Destination account is required")
    private String toAccount;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;  // FIX: BigDecimal, not double

    @Size(max = 500)
    private String description;
}

// Transfer Response
@Builder
public class TransferResponse {
    private String transferId;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private String status;
    private Instant timestamp;
}

// Balance Response
public record BalanceResponse(
    String accountId,
    BigDecimal balance,
    Instant asOf
) {}
```

### Custom Exceptions

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedException extends RuntimeException { }

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)  // 422
public class InsufficientBalanceException extends RuntimeException { }

@ResponseStatus(HttpStatus.FORBIDDEN)
public class FraudDetectedException extends RuntimeException { }
```

### Account Entity with Optimistic Locking

```java
@Entity
public class Account {
    @Id
    private String id;

    private String ownerId;

    @Column(precision = 19, scale = 4)
    private BigDecimal balance;  // FIX: BigDecimal

    @Version  // FIX: Optimistic locking for race conditions
    private Long version;
}
```

### Key Fixes Summary (Same as Tide Interview!)

| Issue | Original | Fixed |
|-------|----------|-------|
| `staffOverride` from client | `@RequestParam boolean staffOverride` | `@PreAuthorize("hasRole('STAFF')")` |
| Money type | `double` | `BigDecimal` |
| Transaction | Missing | `@Transactional(rollbackFor = Exception.class)` |
| Cache consistency | Static HashMap | Removed (or use Redis properly) |
| Authorization | Client-controlled | `Objects.equals(owner, currentUser)` |
| Balance check | `>` | `compareTo() >= 0` |
| Transfer ID | `System.currentTimeMillis() + Random` | `UUID.randomUUID()` |
| Thread safety | HashMap | ConcurrentHashMap or database |
| Response | `void` | `ResponseEntity<TransferResponse>` |
| Audit | Minimal | Detailed with all fields |
| Fraud bypass | `&& !staffOverride` | No bypass possible |

### Transaction Behavior

```
┌────────────────────────────────────────────────────────────┐
│                    @Transactional                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. BEGIN TRANSACTION                                  │  │
│  │ 2. debit(fromAccount)   ← Executed but not committed │  │
│  │ 3. credit(toAccount)    ← Executed but not committed │  │
│  │ 4. save(transfer)       ← Executed but not committed │  │
│  │ 5. COMMIT               ← All changes permanent      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  If ANY step fails:                                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ ROLLBACK                ← All changes discarded      │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

</details>

