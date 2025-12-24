# Technical Deep-Dive Guide - Hiring Manager Round

> Quick reference for technical concepts you'll discuss in the Hiring Manager round

---

## 🏗️ System Design & Architecture

### Scalability Patterns

#### Horizontal vs Vertical Scaling

**Vertical Scaling (Scale Up)**:
- Add more resources to existing server (CPU, RAM)
- **Pros**: Simple, no code changes
- **Cons**: Limited by hardware, single point of failure
- **When**: Small to medium scale, quick fix

**Horizontal Scaling (Scale Out)**:
- Add more servers
- **Pros**: Unlimited scale, fault tolerance
- **Cons**: Requires load balancing, stateless design
- **When**: Production systems, high scale

**Example**:
```
Payment Gateway:
- Vertical: Upgrade server to 32GB RAM, 16 cores
- Horizontal: Add 10 servers behind load balancer
```

---

#### Load Balancing Strategies

**Round Robin**:
- Distribute requests evenly
- **Use Case**: Equal server capacity

**Weighted Round Robin**:
- Distribute based on server capacity
- **Use Case**: Servers with different specs

**Least Connections**:
- Route to server with fewest active connections
- **Use Case**: Long-lived connections

**IP Hash**:
- Route based on client IP
- **Use Case**: Session affinity needed

**Geographic**:
- Route to nearest data center
- **Use Case**: Global applications

---

#### Database Sharding

**What**: Split database into smaller pieces (shards)

**Strategies**:
1. **Range-based**: Shard by user ID range
   - Shard 1: User IDs 1-1000
   - Shard 2: User IDs 1001-2000

2. **Hash-based**: Hash user ID, modulo number of shards
   - More even distribution

3. **Directory-based**: Lookup table for shard mapping
   - More flexible, but single point of failure

**Challenges**:
- Cross-shard queries
- Rebalancing when adding shards
- Transaction consistency

**Example**:
```
Payment Gateway:
- Shard by merchant ID
- Each shard handles subset of merchants
- Cross-shard queries for reporting (use read replicas)
```

---

#### Caching Strategies

**Cache-Aside (Lazy Loading)**:
```
1. Check cache
2. If miss, read from DB
3. Write to cache
4. Return data
```
- **Use Case**: Read-heavy workloads
- **Pros**: Simple, cache failures don't break app
- **Cons**: Cache miss penalty

**Write-Through**:
```
1. Write to DB
2. Write to cache
3. Return success
```
- **Use Case**: Write-heavy, need consistency
- **Pros**: Cache always up-to-date
- **Cons**: Write latency

**Write-Back (Write-Behind)**:
```
1. Write to cache
2. Return success
3. Async write to DB
```
- **Use Case**: High write throughput
- **Pros**: Low latency
- **Cons**: Risk of data loss

**Cache Eviction Policies**:
- **LRU** (Least Recently Used): Evict oldest
- **LFU** (Least Frequently Used): Evict least used
- **TTL** (Time To Live): Expire after time

**Example**:
```
Payment Gateway:
- Cache bank success rates (Redis, TTL: 5 minutes)
- Cache-Aside pattern
- LRU eviction for memory management
```

---

### Microservices Architecture

#### Service Boundaries

**How to Split**:
- **Domain-Driven Design**: Split by business domain
- **Data Ownership**: Each service owns its data
- **Communication**: Minimize inter-service calls

**Example**:
```
Payment Gateway:
- User Service (user data)
- Payment Service (transactions)
- Bank Service (bank integrations)
- Notification Service (emails, SMS)
```

---

#### Inter-Service Communication

**Synchronous (REST, gRPC)**:
- **REST**: HTTP/JSON, simple, widely supported
- **gRPC**: HTTP/2, binary, faster, type-safe
- **Use Case**: Request-response, need immediate response

**Asynchronous (Message Queue)**:
- **RabbitMQ**: AMQP, complex routing
- **Kafka**: High throughput, event streaming
- **Use Case**: Fire-and-forget, event-driven

**Example**:
```
Payment Gateway:
- REST: Payment API (need immediate response)
- Kafka: Payment events (for analytics, notifications)
```

---

#### API Gateway Pattern

**What**: Single entry point for all client requests

**Responsibilities**:
- Routing to appropriate service
- Authentication/Authorization
- Rate limiting
- Request/Response transformation
- Load balancing

**Example**:
```
Client → API Gateway → Payment Service
                    → User Service
                    → Bank Service
```

---

#### Service Discovery

**Problem**: Services need to find each other

**Solutions**:
1. **Client-Side**: Client queries registry (Eureka, Consul)
2. **Server-Side**: Load balancer queries registry

**Example**:
```
Payment Service needs Bank Service:
1. Query service registry
2. Get Bank Service instances
3. Load balance across instances
```

---

### Database Design

#### Normalization vs Denormalization

**Normalization** (3NF):
- Eliminate redundancy
- **Pros**: Data consistency, less storage
- **Cons**: More joins, slower reads

**Denormalization**:
- Add redundancy for performance
- **Pros**: Faster reads, fewer joins
- **Cons**: More storage, update complexity

**Example**:
```
Payment Transaction:
- Normalized: Transaction table + User table (join needed)
- Denormalized: Transaction table includes user_name (faster reads)
```

---

#### Indexing Strategies

**B-Tree Index**:
- Default in most databases
- **Use Case**: Range queries, equality
- **Example**: `WHERE user_id = 123`

**Hash Index**:
- O(1) lookup
- **Use Case**: Equality only
- **Example**: `WHERE email = 'user@example.com'`

**Composite Index**:
- Multiple columns
- **Order matters**: (user_id, created_at) vs (created_at, user_id)

**Example**:
```
Payment Transactions:
- Index on (merchant_id, created_at) for merchant reports
- Index on status for filtering
```

---

## 🔒 Security

### Authentication & Authorization

#### OAuth 2.0 Flow

**Authorization Code Flow** (Most Secure):
```
1. User → Authorization Server (login)
2. Authorization Server → User (authorization code)
3. User → Client App (code)
4. Client App → Authorization Server (code + client_secret)
5. Authorization Server → Client App (access_token)
```

**Use Case**: Web applications, mobile apps

---

#### JWT Tokens

**Structure**: Header.Payload.Signature

**Payload Example**:
```json
{
  "sub": "user123",
  "exp": 1234567890,
  "roles": ["USER", "ADMIN"]
}
```

**Pros**:
- Stateless (no server-side storage)
- Self-contained (includes user info)

**Cons**:
- Can't revoke easily (need blacklist)
- Size limit (can't put too much data)

**Security**:
- Always use HTTPS
- Sign with strong secret
- Set short expiration
- Use refresh tokens

---

#### Role-Based Access Control (RBAC)

**Roles**: USER, ADMIN, MERCHANT

**Permissions**: 
- USER: View own payments
- MERCHANT: View own transactions
- ADMIN: View all transactions

**Implementation**:
```java
@PreAuthorize("hasRole('ADMIN')")
public List<Transaction> getAllTransactions() {
    // Only admins can access
}
```

---

### API Security

#### Rate Limiting

**Why**: Prevent abuse, DDoS protection

**Strategies**:
1. **Token Bucket**: Allow X requests per time window
2. **Sliding Window**: Track requests in time window
3. **Fixed Window**: Reset counter at interval

**Implementation**:
```java
// Using Redis
String key = "rate_limit:" + userId;
Long count = redis.incr(key);
if (count == 1) {
    redis.expire(key, 60); // 60 seconds
}
if (count > 100) {
    throw new RateLimitExceededException();
}
```

---

#### Input Validation

**Always Validate**:
- Type checking
- Range validation
- Format validation (email, phone)
- SQL injection prevention (parameterized queries)
- XSS prevention (sanitize HTML)

**Example**:
```java
@PostMapping("/payments")
public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request) {
    // @Valid triggers Bean Validation
    // PaymentRequest has @NotNull, @Min, @Max annotations
}
```

---

#### SQL Injection Prevention

**❌ Vulnerable**:
```java
String query = "SELECT * FROM users WHERE id = " + userId;
// If userId = "1 OR 1=1", returns all users!
```

**✅ Safe**:
```java
String query = "SELECT * FROM users WHERE id = ?";
PreparedStatement stmt = conn.prepareStatement(query);
stmt.setString(1, userId); // Parameterized query
```

---

### Financial Security

#### PCI DSS Compliance

**If handling card data**:
- Encrypt card numbers
- Don't store CVV
- Use tokenization
- Regular security audits

**Best Practice**: Use payment processors (Stripe, Razorpay) - they handle PCI compliance

---

#### Encryption

**At Rest**: Encrypt database
- **Example**: AES-256 encryption for sensitive fields

**In Transit**: HTTPS/TLS
- **Example**: Always use HTTPS for APIs

**Key Management**:
- Use key management services (AWS KMS, HashiCorp Vault)
- Rotate keys regularly
- Never hardcode keys

---

#### Audit Logging

**What to Log**:
- All financial transactions
- Authentication attempts
- Authorization failures
- Data access (who accessed what)
- Configuration changes

**Requirements**:
- Immutable (can't be modified)
- Tamper-proof
- Retention policy (7 years for financial data)

**Example**:
```java
@Audited
@Entity
public class Payment {
    // Hibernate Envers automatically creates audit table
}
```

---

## 💾 Transactions & Data Consistency

### ACID Properties

**Atomicity**: All or nothing
- **Example**: Payment debit + credit must both succeed or both fail

**Consistency**: Valid state transitions
- **Example**: Account balance can't go negative

**Isolation**: Concurrent transactions don't interfere
- **Example**: Two payments don't see each other's uncommitted changes

**Durability**: Committed changes persist
- **Example**: After payment succeeds, it's saved even if server crashes

---

### Isolation Levels

**READ UNCOMMITTED**:
- Can read uncommitted data
- **Problem**: Dirty reads
- **Use Case**: Rarely used

**READ COMMITTED** (Default in PostgreSQL):
- Can only read committed data
- **Problem**: Non-repeatable reads
- **Use Case**: Most applications

**REPEATABLE READ** (Default in MySQL):
- Same read returns same result
- **Problem**: Phantom reads
- **Use Case**: Need consistency within transaction

**SERIALIZABLE**:
- Highest isolation
- **Problem**: Performance (locks everything)
- **Use Case**: Critical financial operations

**Example**:
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferMoney(String from, String to, BigDecimal amount) {
    // Highest isolation for money transfer
}
```

---

### Locking Strategies

#### Optimistic Locking

**How**: Use version field
```java
@Entity
public class Account {
    @Version
    private Long version; // Auto-incremented on update
    
    private BigDecimal balance;
}

// Usage
Account account = accountRepository.findById(id);
account.setBalance(newBalance);
try {
    accountRepository.save(account); // Fails if version changed
} catch (OptimisticLockingFailureException e) {
    // Retry logic
}
```

**Pros**: No locks, better performance
**Cons**: Need retry logic, can fail under high contention
**Use Case**: Low contention, read-heavy

---

#### Pessimistic Locking

**How**: Lock row during transaction
```java
@Transactional
public void transferMoney(String fromId, String toId, BigDecimal amount) {
    Account from = accountRepository.findByIdWithLock(fromId); // SELECT FOR UPDATE
    Account to = accountRepository.findByIdWithLock(toId);
    
    // Both rows locked until transaction commits
    from.debit(amount);
    to.credit(amount);
}
```

**Pros**: Guaranteed consistency
**Cons**: Lock contention, can cause deadlocks
**Use Case**: High contention, critical operations

---

### Distributed Transactions

#### Two-Phase Commit (2PC)

**How**:
1. **Prepare Phase**: Coordinator asks all participants to prepare
2. **Commit Phase**: If all prepared, coordinator tells all to commit

**Problem**: 
- Blocking (if coordinator fails, participants wait)
- Not suitable for microservices

**Use Case**: Rarely used in microservices

---

#### SAGA Pattern

**How**: Break transaction into steps, each step has compensating action

**Example - Payment Refund**:
```
1. Refund Payment (if fails → nothing to compensate)
2. Update Order Status (if fails → re-charge payment)
3. Send Notification (if fails → nothing critical)
```

**Types**:
- **Choreography**: Each service knows what to do next
- **Orchestration**: Central coordinator manages flow

**Pros**: Non-blocking, works across services
**Cons**: Complex, eventual consistency

**Use Case**: Microservices, long-running transactions

---

## 🔄 Idempotency

### Definition

**Idempotent Operation**: Performing it multiple times has the same effect as once

**Examples**:
- GET request (always safe)
- PUT request (replace resource)
- DELETE request (idempotent)

**Non-Idempotent**:
- POST request (creates new resource each time)

---

### Implementation Strategies

#### 1. Idempotency Keys

**Client sends unique key with request**:
```java
POST /api/payments
Headers: Idempotency-Key: uuid-12345
Body: { amount: 1000, accountId: "ACC123" }
```

**Server checks cache**:
```java
public PaymentResponse processPayment(PaymentRequest request) {
    String key = request.getIdempotencyKey();
    
    // Check Redis cache
    PaymentResponse cached = redis.get("idempotency:" + key);
    if (cached != null) {
        return cached; // Return cached response
    }
    
    // Process payment
    PaymentResponse response = executePayment(request);
    
    // Store response
    redis.setex("idempotency:" + key, 86400, response); // 24 hours
    
    return response;
}
```

**Database Unique Constraint**:
```sql
CREATE TABLE payments (
    idempotency_key VARCHAR(255) UNIQUE,
    -- other fields
);
```

---

#### 2. Natural Idempotency

**Use unique identifiers**:
```java
// Payment with unique transaction ID
Payment payment = paymentRepository.findByTransactionId(txnId);
if (payment != null) {
    return payment; // Already processed
}
// Create new payment
```

---

#### 3. Idempotent HTTP Methods

**PUT**: Replace entire resource (idempotent)
```java
PUT /api/users/123
Body: { name: "John", email: "john@example.com" }
// Same request multiple times = same result
```

**PATCH**: Partial update (may not be idempotent)
```java
PATCH /api/users/123
Body: { balance: balance + 100 } // Not idempotent!
```

---

### Best Practices

1. **Client-Generated Keys**: Let client generate UUID
2. **Storage**: Redis for fast lookup, DB for persistence
3. **TTL**: Set expiration (24 hours typical)
4. **Response Caching**: Store full response, not just status
5. **Error Handling**: Return same error for duplicate key

---

## ⚡ Concurrency

### Race Conditions

**Problem**: Multiple threads access shared state

**Example - Booking System**:
```java
// Thread 1 and Thread 2 both execute:
if (class.getAvailableSlots() > 0) {
    class.bookSlot(); // Both can succeed!
}
```

**Solution**: Synchronization
```java
synchronized (class) {
    if (class.getAvailableSlots() > 0) {
        class.bookSlot();
    }
}
```

---

### Synchronization Mechanisms

#### 1. Synchronized Keyword

```java
public synchronized void transferMoney(Account from, Account to, BigDecimal amount) {
    // Only one thread can execute at a time
}
```

**Pros**: Simple
**Cons**: Coarse-grained, can cause deadlocks

---

#### 2. ReentrantLock

```java
private final ReentrantLock lock = new ReentrantLock();

public void transferMoney(Account from, Account to, BigDecimal amount) {
    lock.lock();
    try {
        // Critical section
    } finally {
        lock.unlock(); // Always unlock
    }
}
```

**Pros**: More flexible (tryLock, timeout)
**Cons**: Must remember to unlock

---

#### 3. Atomic Operations

```java
private final AtomicInteger counter = new AtomicInteger(0);

public void increment() {
    counter.incrementAndGet(); // Thread-safe
}
```

**Use Case**: Simple counters, flags

---

#### 4. Concurrent Collections

**ConcurrentHashMap**:
```java
Map<String, Integer> map = new ConcurrentHashMap<>();
// Thread-safe, no need for synchronization
```

**BlockingQueue**:
```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
queue.put(task); // Blocks if full
Task task = queue.take(); // Blocks if empty
```

---

### Distributed Concurrency

#### Distributed Locks (Redis)

**Problem**: Multiple servers need to coordinate

**Solution**: Redis distributed lock
```java
String lockKey = "booking_lock:" + classId;
String lockValue = UUID.randomUUID().toString();

// Try to acquire lock (with expiration)
boolean acquired = redis.setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

if (acquired) {
    try {
        // Critical section
        bookClass(classId);
    } finally {
        // Release lock (only if we own it)
        if (lockValue.equals(redis.get(lockKey))) {
            redis.delete(lockKey);
        }
    }
}
```

**Use Case**: Preventing duplicate operations across servers

---

#### Leader Election

**Problem**: Only one server should perform certain tasks

**Solution**: ZooKeeper, etcd, Redis
```java
// Try to become leader
boolean isLeader = redis.setIfAbsent("leader", serverId, 30, TimeUnit.SECONDS);

if (isLeader) {
    // This server is leader, perform tasks
    schedulePeriodicTasks();
}
```

---

### Deadlock Prevention

**Deadlock Conditions** (all must be true):
1. Mutual exclusion
2. Hold and wait
3. No preemption
4. Circular wait

**Prevention**:
1. **Lock Ordering**: Always acquire locks in same order
2. **Timeout**: Use tryLock with timeout
3. **Avoid Nested Locks**: Minimize lock nesting

**Example**:
```java
// Always lock accounts in same order (by ID)
if (fromId.compareTo(toId) < 0) {
    lock(fromAccount);
    lock(toAccount);
} else {
    lock(toAccount);
    lock(fromAccount);
}
```

---

## 📊 Performance Optimization

### Database Optimization

**Indexing**:
- Index frequently queried columns
- Composite indexes for multi-column queries
- Don't over-index (slows writes)

**Query Optimization**:
- Use EXPLAIN to analyze queries
- Avoid SELECT * (fetch only needed columns)
- Use pagination for large result sets

**Connection Pooling**:
- Reuse database connections
- Configure pool size appropriately

---

### Caching

**When to Cache**:
- Frequently accessed data
- Expensive computations
- External API responses

**Cache Invalidation**:
- TTL-based expiration
- Event-driven invalidation
- Manual invalidation

---

### Async Processing

**When**: Long-running operations

**Example**:
```java
@Async
public CompletableFuture<Void> sendEmail(String email, String content) {
    // Non-blocking email sending
    emailService.send(email, content);
    return CompletableFuture.completedFuture(null);
}
```

**Use Case**: Notifications, reports, file processing

---

## 🎯 Quick Reference

### When to Use What

| Scenario | Solution |
|----------|----------|
| Prevent double-booking | Pessimistic locking (SELECT FOR UPDATE) |
| High read contention | Optimistic locking + retry |
| Prevent duplicate payments | Idempotency keys |
| Cross-service transactions | SAGA pattern |
| Coordinate across servers | Distributed locks (Redis) |
| Cache frequently accessed data | Redis with TTL |
| Handle high write load | Write-back cache |
| Long-running operations | Async processing |
| Prevent SQL injection | Parameterized queries |
| API rate limiting | Token bucket (Redis) |

---

**Remember**: Always consider trade-offs. There's no one-size-fits-all solution!

