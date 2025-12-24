# 🔄 Deep Dive: Transactions in Spring Boot

A comprehensive guide to understanding transactions, when to use them, and advanced patterns.

---

## 📖 Table of Contents

1. [What is @Transactional?](#1-what-is-transactional)
2. [When to Use @Transactional](#2-when-to-use-transactional)
3. [How It Works Behind the Scenes](#3-how-it-works-behind-the-scenes)
4. [Propagation Types](#4-propagation-types)
5. [Isolation Levels](#5-isolation-levels)
6. [Optimistic vs Pessimistic Locking](#6-optimistic-vs-pessimistic-locking)
7. [Two-Phase Commit (2PC)](#7-two-phase-commit-2pc)
8. [SAGA Pattern](#8-saga-pattern)
9. [Quick Reference Cheatsheet](#9-quick-reference-cheatsheet)
10. [Interview Questions](#10-interview-questions)

---

## 1. What is @Transactional?

### Definition
`@Transactional` ensures that a group of database operations either **ALL succeed** or **ALL fail** together (atomicity).

### Simple Example

```java
// WITHOUT @Transactional - DANGEROUS!
public void transferMoney(String fromAccount, String toAccount, BigDecimal amount) {
    accountRepository.debit(fromAccount, amount);   // ✅ Success
    // 💥 Exception occurs here!
    accountRepository.credit(toAccount, amount);    // ❌ Never executes
    // Result: Money disappeared! Debited but never credited.
}

// WITH @Transactional - SAFE!
@Transactional
public void transferMoney(String fromAccount, String toAccount, BigDecimal amount) {
    accountRepository.debit(fromAccount, amount);   // Executed
    // 💥 Exception occurs here!
    accountRepository.credit(toAccount, amount);    // Never executes
    // Result: BOTH operations rolled back. Money stays in fromAccount.
}
```

### ACID Properties

| Property | Description | Example |
|----------|-------------|---------|
| **A**tomicity | All or nothing | Both debit AND credit happen, or neither |
| **C**onsistency | Valid state before and after | Total money in system stays same |
| **I**solation | Transactions don't interfere | Two transfers don't mix up |
| **D**urability | Committed = permanent | Once confirmed, survives crash |

---

## 2. When to Use @Transactional

### ✅ USE When:

#### 1. Multiple Database Operations That Must Be Atomic
```java
@Transactional
public void createOrder(OrderRequest request) {
    Order order = orderRepository.save(new Order(...));      // Step 1
    inventoryService.reserveStock(order.getItems());         // Step 2
    paymentService.processPayment(order.getTotal());         // Step 3
    notificationService.sendConfirmation(order);             // Step 4
    // If step 3 fails, steps 1 and 2 are rolled back
}
```

#### 2. Financial Operations (Money Transfer)
```java
@Transactional
public void transferMoney(String from, String to, BigDecimal amount) {
    accountService.debit(from, amount);
    accountService.credit(to, amount);
    transactionLogService.log(from, to, amount);
}
```

#### 3. Data Integrity Across Multiple Tables
```java
@Transactional
public void registerUser(UserDTO dto) {
    User user = userRepository.save(new User(dto));
    Profile profile = profileRepository.save(new Profile(user));
    Settings settings = settingsRepository.save(new Settings(user));
    // All three must be created together
}
```

#### 4. Read Operations Needing Consistent View
```java
@Transactional(readOnly = true)
public ReportDTO generateReport(String accountId) {
    BigDecimal balance = accountRepository.getBalance(accountId);
    List<Transaction> txns = transactionRepository.findByAccount(accountId);
    // Ensures balance and transactions are from same point in time
}
```

### ❌ DON'T USE When:

#### 1. Single Database Operation
```java
// Not needed - single operation is already atomic
public User findUser(String id) {
    return userRepository.findById(id).orElse(null);
}
```

#### 2. External API Calls Only
```java
// Can't rollback external APIs!
public void sendNotification(String userId) {
    emailService.send(...);    // Can't undo if fails later
    smsService.send(...);      // External - not transactional
}
```

#### 3. Long-Running Operations
```java
// BAD: Holds database connection too long
@Transactional
public void processLargeFile(MultipartFile file) {
    // Parses 10GB file... takes 30 minutes
    // Database connection held entire time!
}
```

---

## 3. How It Works Behind the Scenes

### Spring's Proxy Magic

When you add `@Transactional`, Spring creates a **proxy** around your bean:

```
┌─────────────────────────────────────────────────────────────┐
│                        Caller                                │
│                           │                                  │
│                           ▼                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Transaction Proxy                        │   │
│  │  ┌────────────────────────────────────────────────┐  │   │
│  │  │ 1. Begin Transaction                           │  │   │
│  │  │ 2. Get DB Connection from Pool                 │  │   │
│  │  │ 3. Set Auto-Commit = false                     │  │   │
│  │  └────────────────────────────────────────────────┘  │   │
│  │                        │                              │   │
│  │                        ▼                              │   │
│  │  ┌────────────────────────────────────────────────┐  │   │
│  │  │         Your Actual Method                     │  │   │
│  │  │  - debit()                                     │  │   │
│  │  │  - credit()                                    │  │   │
│  │  │  - save()                                      │  │   │
│  │  └────────────────────────────────────────────────┘  │   │
│  │                        │                              │   │
│  │                        ▼                              │   │
│  │  ┌────────────────────────────────────────────────┐  │   │
│  │  │ 4. If success → COMMIT                         │  │   │
│  │  │    If exception → ROLLBACK                     │  │   │
│  │  │ 5. Return connection to pool                   │  │   │
│  │  └────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### What Happens Step by Step

```java
@Service
public class MoneyService {

    @Transactional
    public void transfer(String from, String to, BigDecimal amount) {
        // Before this line executes:
        // 1. Proxy intercepts call
        // 2. Gets connection from HikariCP pool
        // 3. Runs: SET autocommit = 0 (or BEGIN TRANSACTION)
        
        accountRepo.debit(from, amount);
        // Runs: UPDATE accounts SET balance = balance - 100 WHERE id = 'A'
        // NOT committed yet - held in transaction log
        
        accountRepo.credit(to, amount);
        // Runs: UPDATE accounts SET balance = balance + 100 WHERE id = 'B'
        // Still not committed
        
        // After method completes successfully:
        // 4. Proxy runs: COMMIT
        // 5. Both updates now permanent
        // 6. Connection returned to pool
        
        // If ANY exception:
        // 4. Proxy runs: ROLLBACK
        // 5. Both updates discarded
        // 6. Connection returned to pool
    }
}
```

### Common Gotcha: Self-Invocation

```java
@Service
public class OrderService {

    public void processOrder(Order order) {
        // ❌ WRONG! @Transactional won't work!
        this.saveOrder(order);  // Direct call bypasses proxy
    }
    
    @Transactional
    public void saveOrder(Order order) {
        orderRepo.save(order);
        itemRepo.saveAll(order.getItems());
    }
}
```

**Fix**: Inject self or restructure:
```java
@Service
public class OrderService {
    
    @Autowired
    private OrderService self;  // Inject proxy
    
    public void processOrder(Order order) {
        self.saveOrder(order);  // ✅ Goes through proxy
    }
}
```

---

## 4. Propagation Types

Propagation defines **what happens when a transactional method calls another transactional method**.

### Visual Overview

```
Method A (@Transactional)
    │
    ├── Method B (@Transactional(propagation = ???))
    │
    └── What happens to the transaction?
```

### All Propagation Types

| Type | Behavior | Use Case |
|------|----------|----------|
| `REQUIRED` (default) | Join existing or create new | Most common, default choice |
| `REQUIRES_NEW` | Always create new, suspend current | Independent operation (audit log) |
| `MANDATORY` | Must have existing, error if none | Ensure caller has transaction |
| `SUPPORTS` | Use existing if present, else none | Read operations |
| `NOT_SUPPORTED` | Suspend existing, run without | Non-transactional sub-operation |
| `NEVER` | Error if transaction exists | Ensure no transaction |
| `NESTED` | Nested transaction with savepoint | Partial rollback |

### Detailed Examples

#### REQUIRED (Default)
```java
@Transactional  // Creates Transaction T1
public void methodA() {
    // ... do work in T1
    methodB();  // Also runs in T1
    // ... more work in T1
}

@Transactional(propagation = Propagation.REQUIRED)
public void methodB() {
    // Joins T1 (doesn't create new)
    // If this fails, ALL of T1 rolls back
}
```

#### REQUIRES_NEW
```java
@Transactional
public void transferMoney() {
    accountService.debit(from, amount);     // In main transaction
    auditService.logTransfer(...);          // SEPARATE transaction
    accountService.credit(to, amount);      // In main transaction
    // If credit fails, debit rolls back, but audit log STAYS
}

@Service
public class AuditService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransfer(TransferDetails details) {
        // Independent transaction
        // Commits even if caller rolls back
        auditRepository.save(new AuditEntry(details));
    }
}
```

#### MANDATORY
```java
@Transactional(propagation = Propagation.MANDATORY)
public void updateBalance(Account account, BigDecimal amount) {
    // Will throw exception if called without transaction
    // Ensures this is never called standalone
    account.setBalance(account.getBalance().add(amount));
    accountRepository.save(account);
}
```

#### NESTED
```java
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);
    
    try {
        loyaltyService.awardPoints(order);  // Nested transaction
    } catch (Exception e) {
        // Nested transaction rolls back to savepoint
        // But main transaction continues!
        log.warn("Failed to award points, continuing...");
    }
    
    inventoryService.updateStock(order);  // Still in main transaction
}

@Transactional(propagation = Propagation.NESTED)
public void awardPoints(Order order) {
    // Creates savepoint
    // If fails, only this part rolls back
}
```

---

## 5. Isolation Levels

Isolation defines **how transactions see each other's uncommitted changes**.

### The Problems

| Problem | Description | Example |
|---------|-------------|---------|
| **Dirty Read** | See uncommitted changes | T1 updates balance, T2 reads it, T1 rolls back |
| **Non-Repeatable Read** | Same query, different results | T1 reads balance, T2 updates & commits, T1 reads again - different! |
| **Phantom Read** | New rows appear | T1 counts rows, T2 inserts row & commits, T1 counts again - different! |

### Isolation Levels

| Level | Dirty Read | Non-Repeatable | Phantom | Performance |
|-------|------------|----------------|---------|-------------|
| `READ_UNCOMMITTED` | ⚠️ Yes | ⚠️ Yes | ⚠️ Yes | Fastest |
| `READ_COMMITTED` | ✅ No | ⚠️ Yes | ⚠️ Yes | Fast |
| `REPEATABLE_READ` | ✅ No | ✅ No | ⚠️ Yes | Medium |
| `SERIALIZABLE` | ✅ No | ✅ No | ✅ No | Slowest |

### When to Use What

```java
// Default for most databases (Postgres, Oracle)
@Transactional(isolation = Isolation.READ_COMMITTED)
public void normalOperation() {
    // Good balance of consistency and performance
}

// Financial calculations needing consistent view
@Transactional(isolation = Isolation.REPEATABLE_READ)
public BigDecimal calculateInterest(String accountId) {
    BigDecimal balance = getBalance(accountId);
    List<Transaction> txns = getTransactions(accountId);
    // Same balance if read again - guaranteed
    return calculateInterest(balance, txns);
}

// Critical financial operations
@Transactional(isolation = Isolation.SERIALIZABLE)
public void endOfDaySettlement() {
    // Full isolation - like running alone
    // Highest consistency, lowest concurrency
}
```

### Visual: Dirty Read Problem

```
Timeline:
─────────────────────────────────────────────────────────────→

Transaction T1 (Transfer)          Transaction T2 (Balance Check)
│                                  │
├── Read Balance A: $1000          │
├── Debit A: $100                  │
│   (Balance A now: $900)          │
│                                  ├── Read Balance A: $900 ← Dirty Read!
├── Credit B: $100                 │
├── ROLLBACK! (Error occurred)     │
│   (Balance A back to: $1000)     │
│                                  ├── Show user: "Balance: $900" ← WRONG!
```

**With READ_COMMITTED**: T2 would wait until T1 commits/rollbacks before reading.

---

## 6. Optimistic vs Pessimistic Locking

### The Problem: Concurrent Updates

```
User A reads account balance: $1000
User B reads account balance: $1000
User A adds $100, saves: $1100
User B adds $200, saves: $1200  ← Overwrites A's change!
Expected: $1300, Actual: $1200 (Lost update!)
```

### Pessimistic Locking

**Philosophy**: "I'll lock it so no one else can touch it"

```java
public interface AccountRepository extends JpaRepository<Account, String> {
    
    // Lock row when reading - others wait
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Account findByIdWithLock(@Param("id") String id);
}

@Transactional
public void transferMoney(String fromId, String toId, BigDecimal amount) {
    // Acquires exclusive lock - other transactions WAIT
    Account from = accountRepo.findByIdWithLock(fromId);
    Account to = accountRepo.findByIdWithLock(toId);
    
    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));
    
    // Lock released on commit
}
```

**SQL Generated**:
```sql
SELECT * FROM accounts WHERE id = 'A' FOR UPDATE;
-- Other transactions block here until lock released
```

**Pros**:
- Guaranteed no conflicts
- Simple mental model

**Cons**:
- Lower concurrency
- Risk of deadlocks
- Database connection held longer

### Optimistic Locking

**Philosophy**: "I'll check if someone else changed it before I save"

```java
@Entity
public class Account {
    @Id
    private String id;
    
    private BigDecimal balance;
    
    @Version  // Magic annotation!
    private Long version;
}
```

```java
@Transactional
public void transferMoney(String fromId, String toId, BigDecimal amount) {
    Account from = accountRepo.findById(fromId).orElseThrow();
    // from.version = 1
    
    Account to = accountRepo.findById(toId).orElseThrow();
    
    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));
    
    accountRepo.save(from);  // Checks version!
    accountRepo.save(to);
}
```

**SQL Generated**:
```sql
UPDATE accounts 
SET balance = 900, version = 2 
WHERE id = 'A' AND version = 1;
-- If version changed (someone else updated), 0 rows affected!
-- JPA throws OptimisticLockException
```

**Handling Conflicts**:
```java
@Transactional
public void transferWithRetry(String from, String to, BigDecimal amount) {
    int retries = 3;
    while (retries > 0) {
        try {
            transferMoney(from, to, amount);
            return;  // Success!
        } catch (OptimisticLockException e) {
            retries--;
            if (retries == 0) throw e;
            // Retry with fresh data
        }
    }
}
```

**Pros**:
- Higher concurrency
- No deadlocks
- Connection not held for lock

**Cons**:
- Must handle conflicts
- Retry logic needed
- More complex

### When to Use Which?

| Scenario | Use |
|----------|-----|
| High contention (many updates to same row) | Pessimistic |
| Low contention (rare conflicts) | Optimistic |
| Financial transactions | Pessimistic (safer) |
| User profile updates | Optimistic (rare conflicts) |
| Inventory with many concurrent orders | Pessimistic |
| Blog post edits | Optimistic |

---

## 7. Two-Phase Commit (2PC)

### The Problem: Distributed Transactions

```
┌─────────────────┐        ┌─────────────────┐
│  Order Service  │        │ Payment Service │
│  (Database A)   │        │  (Database B)   │
└────────┬────────┘        └────────┬────────┘
         │                          │
         │  Create Order            │  Charge Card
         │  ✅ Success              │  ✅ Success
         │                          │
         │  Update Inventory        │
         │  ❌ FAILS!               │
         │                          │
         │  ROLLBACK!               │  Already committed!
         │                          │  Can't rollback! 💀
```

### How 2PC Works

```
           ┌─────────────────────┐
           │   Coordinator (TM)   │
           │  Transaction Manager │
           └──────────┬──────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
        ▼                           ▼
┌───────────────┐           ┌───────────────┐
│ Participant A │           │ Participant B │
│  (Database A) │           │  (Database B) │
└───────────────┘           └───────────────┘

PHASE 1: PREPARE (Voting)
─────────────────────────
Coordinator → All: "Can you commit?"
Participant A: "Yes, I'm ready" (writes to log, holds locks)
Participant B: "Yes, I'm ready" (writes to log, holds locks)

PHASE 2: COMMIT (Decision)
─────────────────────────
Coordinator → All: "COMMIT!"
Participant A: *commits*
Participant B: *commits*

OR if any said "No":
Coordinator → All: "ROLLBACK!"
Everyone: *rollbacks*
```

### 2PC in Spring

```java
// Using JTA (Java Transaction API)
@Configuration
public class JtaConfig {
    @Bean
    public PlatformTransactionManager transactionManager() {
        return new JtaTransactionManager();  // Atomikos, Bitronix, etc.
    }
}

@Service
public class OrderService {
    
    @Transactional  // Now spans multiple databases!
    public void createOrder(OrderRequest request) {
        // Writes to Database A
        orderRepository.save(new Order(request));
        
        // Writes to Database B (different datasource)
        inventoryRepository.reserve(request.getItems());
        
        // 2PC ensures both commit or both rollback
    }
}
```

### Problems with 2PC

| Problem | Description |
|---------|-------------|
| **Blocking** | All participants hold locks during prepare |
| **Single point of failure** | Coordinator dies = stuck transactions |
| **Latency** | Two round trips minimum |
| **Not suitable for microservices** | Network partitions, different DBs |

---

## 8. SAGA Pattern

### The Alternative to 2PC for Microservices

Instead of locking everything, execute steps and **compensate** if something fails.

### Choreography-Based SAGA

Each service listens for events and reacts:

```
┌──────────────┐    OrderCreated    ┌──────────────┐
│    Order     │ ──────────────────→│   Payment    │
│   Service    │                    │   Service    │
└──────────────┘                    └──────┬───────┘
       ▲                                   │
       │                          PaymentCompleted
       │                                   │
       │                                   ▼
       │                           ┌──────────────┐
       │          StockReserved    │  Inventory   │
       └───────────────────────────│   Service    │
                                   └──────────────┘

If Payment fails:
┌──────────────┐   PaymentFailed    ┌──────────────┐
│    Order     │ ←─────────────────│   Payment    │
│   Service    │                    │   Service    │
│  (Cancel)    │                    └──────────────┘
└──────────────┘
```

### Orchestration-Based SAGA

Central orchestrator controls the flow:

```java
@Service
public class OrderSagaOrchestrator {
    
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private InventoryService inventoryService;
    
    public void createOrder(OrderRequest request) {
        String orderId = null;
        String paymentId = null;
        String reservationId = null;
        
        try {
            // Step 1: Create Order
            orderId = orderService.create(request);
            
            // Step 2: Process Payment
            paymentId = paymentService.charge(request.getPayment());
            
            // Step 3: Reserve Inventory
            reservationId = inventoryService.reserve(request.getItems());
            
            // Step 4: Confirm Order
            orderService.confirm(orderId);
            
        } catch (PaymentException e) {
            // Compensate: Cancel Order
            if (orderId != null) orderService.cancel(orderId);
            throw e;
            
        } catch (InventoryException e) {
            // Compensate: Refund Payment, Cancel Order
            if (paymentId != null) paymentService.refund(paymentId);
            if (orderId != null) orderService.cancel(orderId);
            throw e;
        }
    }
}
```

### SAGA with Compensation Table

| Step | Action | Compensation |
|------|--------|--------------|
| 1 | Create Order | Cancel Order |
| 2 | Charge Payment | Refund Payment |
| 3 | Reserve Inventory | Release Inventory |
| 4 | Ship Order | (No compensation - can't un-ship) |

### SAGA Frameworks

```java
// Using Spring State Machine
@Configuration
public class OrderSagaConfig {
    
    @Bean
    public StateMachine<OrderState, OrderEvent> stateMachine() {
        return StateMachineBuilder.builder()
            .withConfiguration()
                .initial(OrderState.CREATED)
            .withStates()
                .states(EnumSet.allOf(OrderState.class))
            .withTransitions()
                .withExternal()
                    .source(OrderState.CREATED)
                    .target(OrderState.PAYMENT_PENDING)
                    .event(OrderEvent.PROCESS_PAYMENT)
                .withExternal()
                    .source(OrderState.PAYMENT_PENDING)
                    .target(OrderState.PAYMENT_FAILED)
                    .event(OrderEvent.PAYMENT_DECLINED)
                    .action(compensateOrder())  // Rollback action
            .build();
    }
}
```

### 2PC vs SAGA

| Aspect | 2PC | SAGA |
|--------|-----|------|
| Consistency | Strong (ACID) | Eventual |
| Isolation | Yes | No (intermediate states visible) |
| Complexity | Protocol complexity | Business logic complexity |
| Performance | Slower (blocking) | Faster (no blocking) |
| Failure handling | Automatic rollback | Manual compensation |
| Use case | Single database, low latency | Microservices, high availability |

---

## 9. Quick Reference Cheatsheet

### When to Use @Transactional

```java
// ✅ YES - Multiple related DB operations
@Transactional
public void transfer() { debit(); credit(); }

// ✅ YES - Financial operations  
@Transactional
public void processPayment() { ... }

// ✅ YES - Consistent read
@Transactional(readOnly = true)
public Report generateReport() { ... }

// ❌ NO - Single operation
public User findUser(String id) { return repo.findById(id); }

// ❌ NO - External API calls
public void sendEmail() { emailClient.send(); }
```

### Propagation Quick Reference

```java
@Transactional(propagation = Propagation.REQUIRED)     // Join or create (DEFAULT)
@Transactional(propagation = Propagation.REQUIRES_NEW) // Always new
@Transactional(propagation = Propagation.MANDATORY)    // Must exist
@Transactional(propagation = Propagation.NESTED)       // Savepoint
```

### Isolation Quick Reference

```java
@Transactional(isolation = Isolation.READ_COMMITTED)   // Default, good balance
@Transactional(isolation = Isolation.REPEATABLE_READ)  // Consistent reads
@Transactional(isolation = Isolation.SERIALIZABLE)     // Highest isolation
```

### Locking Quick Reference

```java
// Optimistic (default with @Version)
@Version
private Long version;

// Pessimistic
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Account findWithLock(String id);
```

### Common Attributes

```java
@Transactional(
    readOnly = true,                        // Optimization for reads
    timeout = 30,                           // Seconds before timeout
    rollbackFor = Exception.class,          // Rollback on checked exceptions
    noRollbackFor = BusinessException.class // Don't rollback for this
)
```

---

## 10. Interview Questions

### Q1: When would you NOT use @Transactional?
**Answer**: 
- Single DB operation (already atomic)
- External API calls (can't rollback)
- Long-running operations (holds connection)
- Read-only operations that don't need consistency

### Q2: Explain the difference between Optimistic and Pessimistic locking
**Answer**:
- **Pessimistic**: Lock row when reading, others wait. Use for high contention.
- **Optimistic**: Check version on update, fail if changed. Use for low contention.

### Q3: What is REQUIRES_NEW propagation?
**Answer**: Creates a new independent transaction, suspending the current one. Useful for audit logs that should persist even if main transaction fails.

### Q4: How does @Transactional work internally?
**Answer**: Spring creates a proxy around the bean. The proxy intercepts method calls, begins transaction before, and commits/rollbacks after based on success/failure.

### Q5: What's the problem with self-invocation?
**Answer**: Calling a @Transactional method from within the same class bypasses the proxy, so transaction isn't started. Solution: Inject self or move to different class.

### Q6: When would you use SAGA over 2PC?
**Answer**: 
- Microservices architecture
- Different databases/services
- Need high availability
- Can tolerate eventual consistency

---

## 🎓 Summary

| Concept | One-Line Summary |
|---------|-----------------|
| @Transactional | Makes multiple DB operations atomic (all or nothing) |
| Propagation | How transactions behave when methods call other methods |
| Isolation | How much transactions can see each other's uncommitted data |
| Optimistic Lock | Check version on save, fail if changed |
| Pessimistic Lock | Lock row on read, others wait |
| 2PC | Distributed atomic commit across databases |
| SAGA | Compensating transactions for microservices |

Remember: **For the Tide interview, if you see multiple DB operations without @Transactional, it's a CRITICAL issue!**



