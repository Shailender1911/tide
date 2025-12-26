# 📚 Tide Interview - General Topics Guide

**Covers**: System Design, Transactions, Idempotency, Distributed Systems, Security, Behavioral Questions

---

## 📋 Table of Contents

1. [Transactions](#1-transactions)
2. [Idempotency](#2-idempotency)
3. [Distributed Systems](#3-distributed-systems)
4. [Security](#4-security)
5. [Database & Caching](#5-database--caching)
6. [API Design](#6-api-design)
7. [Behavioral Questions](#7-behavioral-questions)
8. [Leadership & Ownership](#8-leadership--ownership)

---

## 1. Transactions

### **What are ACID Properties?**

| Property | Description | Example |
|----------|-------------|---------|
| **Atomicity** | All or nothing | Transfer: debit + credit both succeed or both fail |
| **Consistency** | Valid state to valid state | Account balance can't go negative |
| **Isolation** | Concurrent txns don't interfere | Two transfers don't see partial state |
| **Durability** | Committed data persists | Data survives system crash |

### **Transaction Isolation Levels**

```
READ UNCOMMITTED → Dirty reads possible
READ COMMITTED   → No dirty reads (default in PostgreSQL)
REPEATABLE READ  → Same query returns same results in txn
SERIALIZABLE     → Strictest, like sequential execution
```

### **How Loan Repayment Handles Transactions**

```java
@Transactional
public void recordPayment(Loan loan, RecordPaymentRequest request) {
    // 1. Create LoanPayment record
    LoanPayment loanPayment = LoanPayment.builder()
        .applicationId(loan.getApplicationId())
        .amountPaid(request.getAmountPaid())
        .paymentMode(request.getPaymentMode())
        .status(request.getPaymentStatus())
        .build();
    
    // 2. Create MerchantSettlement record
    MerchantSettlement settlement = MerchantSettlement.builder()
        .applicationId(loan.getApplicationId())
        .amount(0.0)
        .status(SUCCESS)
        .build();
    
    // Both saved atomically - if one fails, both rollback
    loanPaymentRepository.save(loanPayment);
    merchantSettlementRepository.save(settlement);
}
```

### **Distributed Transactions - Saga Pattern**

When you can't use single DB transaction (multiple services):

```
┌─────────────────────────────────────────────────────────────┐
│                     SAGA PATTERN                             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Step 1: Create LoanPayment (Local DB)                       │
│     │                                                         │
│     ▼ Success                                                 │
│  Step 2: Call Payout Service (External)                      │
│     │                                                         │
│     ├─► Success → Step 3: Post to LMS                        │
│     │                │                                        │
│     │                ├─► Success → Complete                   │
│     │                └─► Failure → Mark FAILED_TO_POST_TO_LMS│
│     │                              (Retry via cron)          │
│     │                                                         │
│     └─► Failure → Mark LoanPayment FAILED                    │
│                   (Compensation: Retry or alert)             │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### **Interview Q&A**

**Q: How do you handle transactions across microservices?**
> "We use the Saga pattern. Each step has a compensating action. For example, in loan repayment:
> 1. Create local payment record
> 2. Call payout service - if fails, mark local record FAILED
> 3. On payout success (webhook), update status and post to LMS
> 4. If LMS fails, mark FAILED_TO_POST_TO_LMS for retry
> 
> Each step is independently retriable. We achieve eventual consistency."

**Q: When would you use two-phase commit vs saga?**
> "2PC for tightly coupled systems with same DB or when strong consistency is required. Saga for microservices where:
> - Services are independent
> - Eventual consistency is acceptable
> - Long-running transactions
> - Need better availability (2PC blocks on coordinator)"

---

## 2. Idempotency

### **What is Idempotency?**
Same request executed multiple times produces same result.

```
POST /payment {amount: 100, idempotencyKey: "abc123"}
→ First call: Creates payment, returns success
→ Second call: Returns same success (no duplicate payment)
```

### **Why It Matters in Payments**

```
┌────────────────────────────────────────────────────────────┐
│  Client → Server: Create Payment                            │
│  Server processes, creates payment                          │
│  Server → Client: Response (TIMEOUT/LOST)                   │
│  Client retries: Create Payment                             │
│                                                              │
│  WITHOUT Idempotency: Duplicate payment created! 💸         │
│  WITH Idempotency: Same payment returned ✅                 │
└────────────────────────────────────────────────────────────┘
```

### **Implementation Strategies**

#### **1. Idempotency Key (Best for APIs)**
```java
@PostMapping("/payment")
public Response createPayment(
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestBody PaymentRequest request) {
    
    // Check if already processed
    Optional<Payment> existing = paymentRepository
        .findByIdempotencyKey(idempotencyKey);
    
    if (existing.isPresent()) {
        return existing.get(); // Return same result
    }
    
    // Process new payment
    Payment payment = processPayment(request);
    payment.setIdempotencyKey(idempotencyKey);
    return paymentRepository.save(payment);
}
```

#### **2. External Reference Number**
```java
// In Loan Repayment - LMS posting
LoanRepaymentRequest request = LoanRepaymentRequest.builder()
    .externalId(loanPayment.getPaymentReferenceNumber()) // Unique per payment
    .transactionAmount(amount)
    .build();

// LMS rejects if externalId already exists
finfluxClient.postLoanRepayment(request, loanId);
```

#### **3. Status-Based Processing**
```java
// Only process payments in specific states
public void processPayment(LoanPayment payment) {
    if (!INITIATED.equals(payment.getStatus())) {
        log.info("Payment already processed, skipping");
        return;
    }
    
    // Process...
    payment.setStatus(PROCESSING);
    // ... actual processing
    payment.setStatus(SUCCESS);
}
```

#### **4. Database Constraints**
```sql
-- Unique constraint prevents duplicates
ALTER TABLE loan_payment 
ADD CONSTRAINT uk_payment_ref UNIQUE (payment_reference_number);

-- Composite unique for webhook deduplication
ALTER TABLE webhook_details 
ADD CONSTRAINT uk_webhook UNIQUE (application_id, event_type, request_id);
```

### **Interview Q&A**

**Q: How do you implement idempotency in payment APIs?**
> "Multiple layers:
> 1. **Idempotency Key header**: Client provides unique key, we check before processing
> 2. **External Reference**: Unique ID per transaction for downstream deduplication
> 3. **Status Machine**: Only process from valid starting states
> 4. **DB Constraints**: Unique constraints as last defense
> 
> In Loan Repayment, we use payment_reference_number as external ID for LMS, and check existing payments before creating new ones."

**Q: What happens if idempotency check itself fails?**
> "We use database transactions with unique constraints as final safeguard. Even if check fails, DB constraint prevents duplicate. We also implement optimistic locking on critical entities."

---

## 3. Distributed Systems

### **CAP Theorem**

```
┌─────────────────────────────────────────────────────────────┐
│                    CAP THEOREM                               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  C - Consistency: Every read gets most recent write          │
│  A - Availability: Every request gets response               │
│  P - Partition Tolerance: System works despite network fails │
│                                                               │
│  You can only have 2 of 3 during network partition:          │
│                                                               │
│  CP Systems: Sacrifice availability (banks, payments)        │
│  AP Systems: Sacrifice consistency (social media, caching)   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### **Eventual Consistency**

```
┌────────────────────────────────────────────────────────────┐
│  Payment Success → Update Local DB → Async Post to LMS     │
│                                                              │
│  Local DB: IMMEDIATELY consistent                           │
│  LMS: EVENTUALLY consistent (via cron/retry)                │
│                                                              │
│  Acceptable because:                                         │
│  - User sees immediate success                              │
│  - LMS gets accurate data eventually                        │
│  - No money lost (local record is source of truth)          │
└────────────────────────────────────────────────────────────┘
```

### **Common Distributed Patterns**

#### **1. Circuit Breaker**
```java
@CircuitBreaker(name = "lms", fallbackMethod = "fallback")
public Response postToLMS(LoanPayment payment) {
    return finfluxClient.postRepayment(payment);
}

public Response fallback(LoanPayment payment, Exception e) {
    // Mark for retry, don't fail the whole flow
    payment.setStatus(FAILED_TO_POST_TO_LMS);
    return Response.builder().status("QUEUED_FOR_RETRY").build();
}
```

#### **2. Retry with Exponential Backoff**
```java
// Retry configuration
@Retryable(
    value = {TransientException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public void postPayment(Payment payment) {
    externalService.post(payment);
}

// Backoff: 1s → 2s → 4s
```

#### **3. Outbox Pattern**
```java
// Instead of calling external service directly
@Transactional
public void processPayment(PaymentRequest request) {
    // 1. Save payment
    Payment payment = paymentRepository.save(new Payment(request));
    
    // 2. Save to outbox (same transaction)
    outboxRepository.save(OutboxEvent.builder()
        .aggregateId(payment.getId())
        .eventType("PAYMENT_CREATED")
        .payload(serialize(payment))
        .build());
}

// Separate process reads outbox and publishes events
```

### **Interview Q&A**

**Q: How do you handle failures in distributed systems?**
> "Multiple strategies:
> 1. **Timeout + Retry**: For transient failures
> 2. **Circuit Breaker**: Prevent cascade failures
> 3. **Fallback**: Graceful degradation
> 4. **Async Processing**: Queue for later processing
> 5. **Idempotency**: Safe to retry
> 
> In Loan Repayment, if payout fails, we mark payment FAILED and retry via cron. If LMS fails, separate retry mechanism."

**Q: How do you ensure data consistency across services?**
> "We use eventual consistency with:
> 1. Local transaction as source of truth
> 2. Async sync to other systems
> 3. Reconciliation jobs to detect mismatches
> 4. Audit trails for debugging
> 
> For critical operations like payments, we use saga pattern with compensating transactions."

---

## 4. Security

### **Authentication vs Authorization**

| Aspect | Authentication | Authorization |
|--------|---------------|---------------|
| **What** | Who are you? | What can you do? |
| **How** | Token, password, API key | Roles, scopes, permissions |
| **Example** | JWT validation | Check user has ADMIN role |

### **Authentication Methods (From Orchestration)**

```java
public boolean authenticateClient(List<String> scopes) {
    HttpServletRequest request = getCurrentRequest();
    
    // Method 1: X-API-Key (internal services)
    String apiKey = request.getHeader("x-api-key");
    if (apiKey != null) {
        return authenticateByXApiKey(apiKey);
    }
    
    // Method 2: OAuth2 Bearer Token (partners)
    String token = getAuthToken(request);
    Response tokenInfo = authService.getTokenInfoFromHuB(token);
    
    // Method 3: Partner UID validation
    String clientUId = getClientUId(tokenInfo);
    PartnerDetails partner = validatePartner(clientUId);
    
    return assertTokenScopes(tokenInfo, scopes);
}
```

### **API Security Best Practices**

#### **1. Input Validation**
```java
@PostMapping("/payment")
public Response createPayment(
    @Valid @RequestBody PaymentRequest request) {
    // @Valid triggers validation annotations
}

public class PaymentRequest {
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotBlank
    @Size(max = 50)
    private String referenceId;
    
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;
}
```

#### **2. Rate Limiting**
```java
@RateLimiter(name = "payment-api", fallbackMethod = "rateLimitFallback")
@PostMapping("/payment")
public Response createPayment(PaymentRequest request) {
    // Process payment
}

public Response rateLimitFallback(PaymentRequest request, Exception e) {
    return Response.error("Too many requests, please retry later");
}
```

#### **3. HMAC Signature (Webhooks)**
```java
// Generating signature (sender)
public String generateMac(Object data) {
    String payload = objectMapper.writeValueAsString(data);
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(salt.getBytes(), "HmacSHA1"));
    byte[] hash = mac.doFinal(payload.getBytes());
    return Base64.getEncoder().encodeToString(hash);
}

// Verifying signature (receiver)
public boolean verifyMac(String receivedMac, Object data) {
    String expectedMac = generateMac(data);
    return MessageDigest.isEqual(
        receivedMac.getBytes(), 
        expectedMac.getBytes()
    );
}
```

#### **4. Sensitive Data Handling**
```java
// Never log sensitive data
log.info("Processing payment for user: {}", maskPAN(cardNumber));

// Use vault for secrets
@Value("${vault.api-key}")
private String apiKey; // Retrieved from AWS KMS/Vault

// Encrypt at rest
@Column(name = "account_number")
@Convert(converter = EncryptedStringConverter.class)
private String accountNumber;
```

### **Interview Q&A**

**Q: How do you secure APIs?**
> "Multiple layers:
> 1. **Authentication**: OAuth2 tokens, API keys, validated via central HUB
> 2. **Authorization**: Scope-based access control per endpoint
> 3. **Input Validation**: Bean validation, sanitization
> 4. **Rate Limiting**: Prevent abuse
> 5. **HTTPS**: All traffic encrypted
> 6. **Audit Logging**: Track all access"

**Q: How do you secure webhooks?**
> "HMAC signature verification. We include a MAC in each webhook calculated using shared secret. Receiver calculates expected MAC and compares. Also:
> - Whitelist webhook URLs
> - Use HTTPS only
> - Include timestamp to prevent replay attacks"

**Q: How do you handle sensitive data?**
> "Defense in depth:
> - Encrypt in transit (TLS)
> - Encrypt at rest (DB encryption)
> - Mask in logs
> - Access control to DB
> - Regular rotation of keys
> - Use vault for secrets (AWS KMS)"

---

## 5. Database & Caching

### **Read-Write Separation**

```
┌────────────────────────────────────────────────────────────┐
│                                                              │
│   Application                                                │
│       │                                                      │
│       ├── WRITES ──────► Primary DB                         │
│       │                      │                               │
│       │                      │ Replication                   │
│       │                      ▼                               │
│       └── READS ───────► Read Replica                       │
│                                                              │
│   Benefits:                                                  │
│   - Scale read operations independently                     │
│   - Reduce load on primary                                  │
│   - Improved read performance                               │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### **Caching Strategies**

#### **1. Cache-Aside (Lazy Loading)**
```java
public TokenInfo getTokenInfo(String token) {
    // Try cache first
    TokenInfo cached = cache.get(token);
    if (cached != null) {
        return cached;
    }
    
    // Miss - fetch from source
    TokenInfo tokenInfo = hubService.validateToken(token);
    
    // Populate cache
    cache.put(token, tokenInfo, TTL);
    
    return tokenInfo;
}
```

#### **2. Write-Through**
```java
public void updateConfig(Config config) {
    // Update DB
    configRepository.save(config);
    
    // Update cache
    cache.put(config.getKey(), config);
}
```

#### **3. Cache Invalidation**
```java
// Time-based expiry
cache.put(key, value, Duration.ofMinutes(30));

// Event-based invalidation
@EventListener
public void onConfigUpdate(ConfigUpdateEvent event) {
    cache.evict(event.getConfigKey());
}
```

### **Redis Usage in Orchestration**

```java
@Service
public class AuthCacheService {
    
    @Autowired
    private RedisTemplate<String, Response> redisTemplate;
    
    private static final long TOKEN_TTL_SECONDS = 3600; // 1 hour
    
    public Optional<Response> getValidTokenFromCache(String token) {
        String cacheKey = "token:" + hash(token);
        return Optional.ofNullable(redisTemplate.opsForValue().get(cacheKey));
    }
    
    public void cacheToken(Response tokenInfo, String token) {
        String cacheKey = "token:" + hash(token);
        redisTemplate.opsForValue().set(cacheKey, tokenInfo, 
            TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
    }
}

// Result: 20% reduction in API latency
```

### **Interview Q&A**

**Q: When would you use caching?**
> "When data is:
> - Read frequently, written infrequently
> - Tolerant to slight staleness
> - Expensive to compute/fetch
> 
> In Orchestration, we cache OAuth tokens to avoid hitting HUB service for every request. Result: 20% latency reduction."

**Q: How do you handle cache invalidation?**
> "Multiple strategies based on use case:
> - **TTL**: For data that can be stale (token cache)
> - **Event-driven**: For critical data (config changes)
> - **Cache-aside**: Let cache naturally refresh on miss
> 
> We use TTL for tokens (1 hour) since token validity is longer."

---

## 6. API Design

### **REST Best Practices**

```
✅ Good API Design:
GET    /api/v1/loans                    → List loans
GET    /api/v1/loans/{id}               → Get specific loan
POST   /api/v1/loans                    → Create loan
PUT    /api/v1/loans/{id}               → Update loan
DELETE /api/v1/loans/{id}               → Delete loan
POST   /api/v1/loans/{id}/payments      → Create payment for loan

❌ Bad API Design:
GET    /api/getLoanById?id=123          → Verbs in URL
POST   /api/createNewLoan               → CRUD in URL
GET    /api/loans/delete/123            → GET for mutations
```

### **Versioning**
```
URL Path:      /api/v1/loans, /api/v2/loans
Header:        Accept: application/vnd.api+json; version=1
Query Param:   /api/loans?version=1

We use URL path versioning - clearest for consumers
```

### **Error Handling**
```java
// Standardized error response
{
    "status": "ERROR",
    "errorCode": "LOAN_NOT_FOUND",
    "message": "Loan with ID 123 not found",
    "timestamp": "2024-01-15T10:30:00Z",
    "path": "/api/v1/loans/123"
}

// Global exception handler
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(LoanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(LoanNotFoundException e) {
        return ResponseEntity.status(404)
            .body(ErrorResponse.builder()
                .errorCode("LOAN_NOT_FOUND")
                .message(e.getMessage())
                .build());
    }
}
```

### **Pagination**
```java
// Request
GET /api/v1/loans?page=0&size=20&sort=createdAt,desc

// Response
{
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true
}
```

---

## 7. Behavioral Questions

### **STAR Method Template**

| Letter | Meaning | Duration |
|--------|---------|----------|
| **S** | Situation - Context | 20% |
| **T** | Task - Your responsibility | 20% |
| **A** | Action - What you did | 40% |
| **R** | Result - Outcome + metrics | 20% |

### **Common Questions & Answers**

#### **Q: Tell me about a challenging project**

> **S**: "In Loan Repayment, we needed to support multiple payment modes for different partners - each with unique configurations and different settlement requirements."
>
> **T**: "I was responsible for designing the settlement split logic and ensuring accurate distribution between lender and merchant."
>
> **A**: "I implemented a split payment analyzer that:
> - Calculates demand from LMS in real-time
> - Handles partial payments by prioritizing high-DPD loans
> - Creates separate records for lender and merchant payments
> - Uses async processing for payout initiation
> - Has retry mechanism for failures"
>
> **R**: "The system now handles daily repayments for 5+ partners with automated settlement. Reduced manual intervention by 80%. Zero settlement errors in production."

#### **Q: Tell me about a time you disagreed with your team**

> **S**: "When implementing webhook retry mechanism, team wanted 7-day retry window. I thought 2 days with exponential backoff was better."
>
> **T**: "Needed to convince team while respecting their concerns about missed webhooks."
>
> **A**: "I analyzed historical data showing 95% of successful retries happen within 48 hours. Created POC showing exponential backoff handles transient failures better. Proposed alerting for webhooks failing after 2 days."
>
> **R**: "Team agreed to 2-day window with backoff. Webhook reliability improved 20%. Reduced unnecessary retries by 60%."

#### **Q: Describe a time you had to learn something quickly**

> **S**: "When assigned to integrate Google Pay as a lending partner, I had no prior experience with their APIs."
>
> **T**: "Had to understand their authentication model, API contracts, and webhook requirements in 2 weeks."
>
> **A**: "I:
> - Read all GPay documentation
> - Set up sandbox environment for testing
> - Built proof of concept for key flows
> - Created reusable partner integration template
> - Documented learnings for team"
>
> **R**: "Completed integration ahead of schedule. Template reduced future partner onboarding time by 30%."

#### **Q: Tell me about a production issue you resolved**

> **S**: "Payout settlements were failing for a specific partner due to incorrect bank account mapping."
>
> **T**: "I was on-call and needed to identify root cause and fix without affecting other partners."
>
> **A**: "I:
> - Analyzed logs to identify pattern (all failures same partner)
> - Traced code to find bank account lookup logic
> - Found config mismatch in partner-specific settings
> - Deployed hotfix with proper config
> - Added validation to prevent similar issues"
>
> **R**: "Resolved within 2 hours. Added monitoring alerts. Zero recurrence."

---

## 8. Leadership & Ownership

### **Questions About Initiative**

#### **Q: Give an example of going beyond your job responsibilities**

> "I noticed partner onboarding was manual and error-prone. Without being asked, I:
> - Designed state machine for application workflow
> - Created configuration-driven partner setup
> - Built automated validation for new partners
> 
> Result: Reduced onboarding time by 30%, fewer production issues."

#### **Q: How do you prioritize when you have multiple tasks?**

> "I use impact/effort matrix:
> - High impact, low effort: Do first
> - High impact, high effort: Plan and schedule
> - Low impact, low effort: Batch together
> - Low impact, high effort: Question if needed
> 
> For production issues: Always priority one, regardless of planned work."

### **Questions About Teamwork**

#### **Q: How do you work with cross-functional teams?**

> "In partner integrations, I work with:
> - **Product**: Understanding requirements, acceptance criteria
> - **QA**: Test case review, edge case identification
> - **DevOps**: Deployment, monitoring setup
> - **Partner teams**: API contracts, testing
> 
> I ensure regular syncs, clear documentation, and no surprises at integration time."

#### **Q: How do you handle code reviews?**

> "I approach reviews collaboratively:
> - Focus on logic, not style (use linters for style)
> - Ask questions instead of demanding changes
> - Appreciate good patterns, not just point out issues
> - Timely reviews (within 24 hours)
> - Document learnings for team"

---

## 🎯 Quick Reference - Topics by Priority

### **Must Know** ⭐⭐⭐
- [ ] Idempotency in payments
- [ ] Distributed transactions (Saga)
- [ ] Authentication methods
- [ ] STAR format answers

### **Should Know** ⭐⭐
- [ ] CAP theorem
- [ ] Caching strategies
- [ ] Database transactions (ACID)
- [ ] API design best practices

### **Good to Know** ⭐
- [ ] Circuit breaker pattern
- [ ] Outbox pattern
- [ ] Rate limiting implementation
- [ ] Read-write separation

---

## 📝 Pre-Interview Checklist

- [ ] Review all Q&A in this document
- [ ] Prepare 3-4 STAR stories
- [ ] Know your projects' key metrics
- [ ] Practice explaining complex concepts simply
- [ ] Prepare questions for interviewer

---

**Remember**: Connect every answer back to your real experience at PayU. Interviewers value practical experience over theoretical knowledge!

**Good luck! 🚀**

