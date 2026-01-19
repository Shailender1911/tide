# 🎯 TIDE INTERVIEW - CROSS-QUESTIONS WITH HONEST ANSWERS

**Based on Actual Codebase - No Fabricated Information**

---

## ⚠️ **IMPORTANT DISCLAIMER**

**What I CAN answer confidently (from codebase):**
- DLS NACH Service architecture
- InsureX Service patterns
- Orchestration webhooks and Redis
- State machine implementation
- Factory/Strategy patterns

**What I need to be HONEST about:**
- Some resume claims may be from previous work or team contributions
- Exact metrics (10x, 40%, etc.) - need to verify measurement methodology
- Some services I may have contributed to but not owned entirely

---

# 📋 DLS NACH SERVICE - DEEP DIVE (Q1-Q8)

## **Q1: Why did you choose Strategy and Factory patterns for NACH types?**

### **Honest Answer:**

```java
// From: dls-nach-service/src/main/java/com/smb/nach_service/service/factory/DigioCallbackServiceFactory.java

@Component
public class DigioCallbackServiceFactory {
    
    private final EnumMap<NachTypeEnum, DigioCallbackService> strategyMap;
    
    public DigioCallbackServiceFactory(List<DigioCallbackService> strategies) {
        this.strategyMap = new EnumMap<>(NachTypeEnum.class);
        for (DigioCallbackService strategy : strategies) {
            this.strategyMap.put(strategy.getNachTye(), strategy);
        }
    }
    
    public DigioCallbackService getStrategy(NachTypeEnum type) {
        return Optional.ofNullable(strategyMap.get(type))
                .orElseThrow(() -> new CallbackNachAPIFlowException(...));
    }
}
```

**Why Factory Pattern:**
> "We have multiple NACH types (UPI Mandate, eNACH, etc.) from Digio. Each type has different callback handling logic. Factory pattern lets Spring auto-discover all implementations via `List<DigioCallbackService>` injection."

**Alternatives Considered:**
```
1. Simple switch-case: Rejected - Violates Open-Closed principle
2. Template Method: Could work, but Strategy better for behavioral variations
3. Abstract Factory: Overkill - we don't need families of related objects
```

---

## **Q2: Walk me through NACH mandate creation flow**

### **Honest Answer:**

```
Flow (based on NachService.java):

1. API Request → NachController
   └── Validation: NachValidationService.validateRequest()

2. Save Initial State
   └── Insert into nach_details table with status = INITIATED

3. Call Digio API (External)
   └── DigioIntegrationService.createMandate()
   └── Save Digio response

4. Callback from Digio (Async)
   └── NachService.handleDigioCallback()
   └── DigioCallbackServiceFactory.getStrategy(nachType)
   └── strategy.consumeCallback()

5. Update Status + Send Partner Callback
   └── strategy.sendPartnerCallback()
   └── Uses TenantCallbackServiceFactory for tenant-specific logic
```

**No Rollback/Compensation:**
> "We don't do Saga-style compensation. We use idempotent retries. Each step is designed to be safe to retry."

---

## **Q3: How do you ensure exactly-once webhook delivery?**

### **Honest Answer:**

> "We DON'T guarantee exactly-once. We guarantee **at-least-once with idempotency**."

```java
// From: WebhookDetails.java
@Entity
public class WebhookDetails {
    private String requestId;      // For deduplication
    private WebhookStatus status;  // PENDING, SUCCESS, FAILED
    private boolean retryRequired; // For retry logic
}
```

**Retry Strategy:**
> "Cron-based retry. Not exponential backoff - simple interval-based."

---

## **Q4: Explain your HMAC-SHA256 validation implementation**

### **Honest Answer:**

```java
// From: ApplicationValidator.java
public void validateCheckSumForGenerateOffer(String applicationId, Integer tenantId, JsonObject dto) {
    String salt = configService.getConfig(tenantId, "GENERATE_OFFER_CHECK_SUM_SALT");
    String key = configService.getConfig(tenantId, "GENERATE_OFFER_CHECK_SUM_KEY");
    // Validate checksum matches
}
```

**Key Storage:** Database config table, per-tenant. Not in code.

**Key Rotation:** Manual process. No automated rotation.

---

## **Q5: What if two mandate creation requests come simultaneously?**

### **Honest Answer:**

**Layer 1: Database Unique Constraint**
```sql
UNIQUE KEY (application_id, nach_type)
```

**Layer 2: Idempotency Check**
```java
Optional<NachDetails> existing = nachRepository.findByApplicationIdAndNachType(appId, type);
if (existing.isPresent()) {
    return existing.get(); // Return existing, don't create new
}
```

**Layer 3: Distributed Lock (ZipCredit only)**
> "NACH service doesn't use distributed locks. ZipCredit uses Redisson locks for critical operations."

---

## **Q6: How do you handle state machine transitions during concurrent updates?**

### **Honest Answer:**

```java
// From: ApplicationStatusServiceImpl.java
public boolean insertApplicationTracker(...) {
    // 1. Mark previous active status as inactive (single UPDATE)
    markcurrentStatusInActiveIfAlreadyAvailable(applicationId, tenantId, currentStatus);
    
    // 2. Save current status (single INSERT)
    saveCurrentStatus(applicationId, tenantId, currentStatus, prevStatus);
    
    // Each operation is atomic. No multi-step transaction.
}
```

**Concurrency Protection:**
- Database-level row locking via `@Transactional`
- `is_active` flag prevents duplicate active states
- No distributed locking for state transitions (single DB)

---

## **Q7: How did you implement multi-tenant data backfilling?**

### **Honest Answer:**

> "We use `tenant_id` column in all tables. Each query includes `WHERE tenant_id = ?`"

```java
// From: TenantConfig in all services
@Entity
public class NachDetails {
    @Column(name = "tenant_id")
    private Integer tenantId;  // Partition key for multi-tenancy
}
```

**Data Isolation:**
- Row-level filtering (not separate schemas)
- `tenant_id` in all queries
- Configuration per tenant in config tables

---

## **Q8: What if backfilling fails for one tenant?**

### **Honest Answer:**

```
1. Each tenant processed separately in batch
2. Failure logged with tenant_id
3. Other tenants continue processing
4. Failed tenant marked for retry
5. Manual intervention for persistent failures
```

**No automatic rollback** - we retry failed tenant independently.

---

# 📋 INSUREX SERVICE - DEEP DIVE (Q9-Q14)

## **Q9: How does Factory pattern handle vendor-specific logic?**

### **Honest Answer:**

```java
// From: InsuranceVendorFactory.java
@Component
public class InsuranceVendorFactory {
    @Autowired private IciciInsuranceVendorImpl iciciInsuranceVendor;
    @Autowired private AckoInsuranceVendorImpl ackoInsuranceVendor;
    
    public InsuranceVendor getInsuranceVendor(String vendorCode) {
        if("ICICI".equalsIgnoreCase(vendorCode)) return iciciInsuranceVendor;
        if("ACKO".equalsIgnoreCase(vendorCode)) return ackoInsuranceVendor;
        return null;
    }
}
```

**Honest Assessment:**
> "Simple if-else for 2 vendors. For more vendors, we'd refactor to use `List<InsuranceVendor>` injection."

---

## **Q10: What if a vendor is down during policy creation?**

### **Honest Answer:**

```java
// Flow:
1. API call to vendor fails (timeout/error)
2. Policy status set to VENDOR_API_FAILED
3. Response returned to caller with failure status
4. Cron job picks up failed policies for retry
```

**No Circuit Breaker:**
> "We don't have circuit breaker implementation. We use simple retry via cron."

---

## **Q11: Why CompletableFuture over plain threads?**

### **Honest Answer:**

```java
// From: InsuranceServiceImpl.java
CompletableFuture.runAsync(() -> 
    policyServiceHelper.processInsurance(request, clientInfoId, policyId), 
    taskExecutor  // Spring-managed ThreadPoolTaskExecutor
).exceptionally(ex -> {
    log.error("Error: {}", ex.getMessage(), ex);
    return null;
});
```

**Why:**
1. Built-in exception handling via `.exceptionally()`
2. Uses managed ThreadPoolTaskExecutor
3. Spring-managed lifecycle
4. Named threads for debugging

---

## **Q12: Explain your two-phase API flow (Policy + COI)**

### **Honest Answer:**

```
Phase 1: Create Policy
- Collect customer consent
- Call vendor API to create policy
- Store policy details

Phase 2: Generate COI (Certificate of Insurance)
- Called after policy confirmed
- Download COI PDF from vendor
- Store for customer download
```

**Why Two Phases:**
> "Business requirement. Consent must be captured before policy. COI generated only after payment confirmed."

**If COI fails after policy:**
> "Policy exists. COI marked for retry. Customer can still download later."

---

## **Q13: How does cron-based retry work?**

### **Honest Answer:**

```java
@Override
public Response insurancePolicyCron(PolicyCronRequest cronRequest) {
    // Find policies with status = FAILED or PENDING
    // Filter by retry_count < MAX_RETRIES
    // Filter by last_attempt_time + RETRY_INTERVAL < NOW
    // Retry each
}
```

**Why Cron over Event-Driven:**
> "Simplicity. No Kafka infrastructure needed. For hundreds of policies/day, cron is sufficient."

---

## **Q14: How do you maintain audit trail for compliance?**

### **Honest Answer:**

```java
// Hibernate Envers for entity auditing
@Entity
@Audited
public class PolicyDetails {
    // All changes tracked automatically
}

// Audit tables: policy_details_AUD
// Tracks: WHO changed WHAT and WHEN
```

**Data Retention:**
> "Audit tables grow indefinitely. No automated cleanup currently."

---

# 📋 STATE MACHINE - DEEP DIVE (Q15-Q16)

## **Q15: Walk me through your state machine design**

### **Honest Answer:**

```java
// NOT a formal state machine library. History-based tracking.

// Table: a_application_stage_tracker
// Each stage is a row with current_status and is_active flag

// From: ApplicationStatusServiceImpl.java
public boolean insertApplicationTracker(String applicationId, Integer tenantId, 
                                        ApplicationStage currentStatus) {
    markcurrentStatusInActiveIfAlreadyAvailable(...);
    saveCurrentStatus(...);
    processTriggers(...);  // Cascade to next stage
    return true;
}
```

**States:** 50+ stages in `ApplicationStage` enum

**Triggers:** `TriggerServiceImpl` maps stage → events → async processing

---

## **Q16: How do you handle state machine failures mid-transition?**

### **Honest Answer:**

```
We DON'T have automatic rollback.

1. Stage A succeeds → saved to DB
2. Trigger for Stage B starts  
3. Stage B fails → logged, NO rollback

Recovery:
- Idempotent retries (check what already succeeded)
- Cron for stuck applications
- Manual intervention for persistent failures
```

---

# 📋 PERFORMANCE OPTIMIZATION (Q17-Q20)

## **Q17: How did you achieve 10x improvement with read-write separation?**

### **Honest Answer:**

```java
// Master for writes, Replica for reads
// Routing via Spring's AbstractRoutingDataSource (if implemented)
```

**Honest Caveat:**
> "I'd need to verify the '10x' measurement. This was likely for specific heavy-read queries that moved to replica. Not system-wide 10x."

---

## **Q18: How do you handle eventual consistency with read replicas?**

### **Honest Answer:**

```
1. Writes go to master
2. Replication lag: typically < 100ms
3. Critical reads (after write): read from master
4. Non-critical reads: can use replica
```

**How we handle:**
> "For critical validations (like loan creation), we bypass cache and read from master DB directly."

---

## **Q19: Explain your batch processing implementation**

### **Honest Answer:**

```java
// From: loan-repayment ChunkedListProcessingStrategy
private static final int PROCESSING_CHUNK_SIZE = 1000;

// Process in chunks instead of all at once
for (List<T> chunk : partition(records, CHUNK_SIZE)) {
    processChunk(chunk);
}
```

**Why it helps:**
- Smaller memory footprint
- Prevents long-running transactions
- Allows other queries to run

---

## **Q20: What rate limiting algorithm did you use?**

### **Honest Answer:**

```java
// From: ZCVersion4ServiceImpl.java
private void validateMaxApplicationLimit(String channelCode, Integer tenantId, String panNumber) {
    int maxLimitByChannelAndPan = getMaxAllowedLimit(tenantId, "APPLICATION_MAX_LIMIT_BY_CHANNEL_PAN");
    int maxLimitByPan = getMaxAllowedLimit(tenantId, "APPLICATION_MAX_LIMIT_BY_PAN");
    
    int totalCountByChannelAndPan = applicationService.getApplicationCountByPanAndChannelCode(...);
    
    if (totalCountByChannelAndPan >= maxLimitByChannelAndPan) {
        throw new ZcV4Exception("Max application limit reached");
    }
}
```

**Algorithm:** Simple counter-based (not token bucket/leaky bucket)

**Per-Tenant:** Yes, limits configurable per tenant

**Distributed:** No Redis-based distributed rate limiting. DB count query.

---

# 📋 LENDING REVAMP (Q21-Q24)

## **Q21: Why Apache PDFBox over iText?**

### **Honest Answer:**

> "PDFBox is Apache licensed (free). iText has commercial licensing for production use."

**Trade-offs:**
- PDFBox: Free, good for basic PDF generation
- iText: More features, but licensing cost

---

## **Q22: How did you ensure 30% reduction in document generation time?**

### **Honest Answer:**

> "I'd need to verify the measurement methodology. Likely measured for specific document types."

**What we optimized:**
- Template caching
- Parallel document generation for independent docs
- Async processing (don't block API response)

---

## **Q23: How do you handle Video KYC failures?**

### **Honest Answer:**

```
1. Hyperverge API call fails
2. Status set to VIDEO_KYC_FAILED
3. User can retry
4. After max retries → manual review queue
```

**No automatic fallback.** Manual review for persistent failures.

---

## **Q24: Explain your wrapper API design for KYC providers**

### **Honest Answer:**

```java
// Common interface
public interface KycProvider {
    KycResponse verifyIdentity(KycRequest request);
}

// Implementations for each vendor
@Service public class HypervergeKycProvider implements KycProvider { }
@Service public class DigilockerKycProvider implements KycProvider { }
```

**Why wrapper:**
- Vendor lock-in prevention
- Common error handling
- Easy to switch providers

---

# 📋 PARTNER INTEGRATIONS (Q25-Q28)

## **Q25: How do you handle different API contracts across partners?**

### **Honest Answer:**

```java
// Adapter pattern per partner
public interface PartnerAdapter {
    LoanResponse createLoan(LoanRequest request);
    StatusResponse getStatus(String applicationId);
}

// Each partner has adapter implementation
@Service public class GPayAdapter implements PartnerAdapter { }
@Service public class PhonePeAdapter implements PartnerAdapter { }
```

**Partner-specific quirks:** Handled in adapter, not in core logic.

---

## **Q26: How do you monitor transaction volumes across partners?**

### **Honest Answer:**

```
1. Structured logs with partner_code
2. Coralogix/Kibana dashboards filtered by partner
3. SigNoz APM for latency per partner
4. Grafana for success/failure rates
```

**No automated anomaly detection.** Manual dashboards.

---

## **Q27: Explain Redis caching strategy for Orchestration**

### **Honest Answer:**

```java
// From: CustomRedisCacheManager.java
private long getTtl(String cacheName) {
    return ttlConfig.getOrDefault(cacheName, TimeUnit.HOURS.toMillis(168)); // 7 days default
}

// Usage
@Cacheable(value = "applicationCache", key = "#applicationId")
ApplicationDetails findByLosApplicationId(String applicationId);
```

**What we cache:** Application details, partner config, auth tokens

**Pattern:** Cache-Aside (check cache → miss → DB → populate cache)

---

## **Q28: How do you handle cache consistency?**

### **Honest Answer:**

**The cache race condition was in ZipCredit service, NOT Orchestration.**

**Our Fix (Commit 31ed9d129f):**
```java
// Bypass cache for critical validations
applicationTrackerBeanList = applicationTrackerService
    .selectApplicationTrackerFromDB(applicationId, tenantId);

// Added retry with exponential backoff
int maxRetries = 3;
int retryDelayMs = 100;
for (int attempt = 1; attempt <= maxRetries; attempt++) {
    if (validationPasses) return;
    Thread.sleep(retryDelayMs);
    retryDelayMs *= 2;
}
```

**Redis Cluster Mode:**
> "Production runs Redis in CLUSTER mode for HA and scalability."

---

# 📋 AI-ENHANCED DEVELOPMENT (Q29-Q30)

## **Q29: How are you using Cursor AI in your workflow?**

### **Honest Answer:**

```
1. Code generation: Boilerplate, DTOs, tests
2. Code review assistance: Pattern suggestions
3. Documentation: API docs, README
4. Debugging: Analyzing stack traces
5. Refactoring: Identifying code smells
```

**Where I use it most:** ConfigNexus project - built 80% with AI assistance.

---

## **Q30: What are limitations of AI-assisted development?**

### **Honest Answer:**

```
1. Can generate plausible but incorrect code
2. Doesn't understand business context
3. Security: Don't paste secrets in prompts
4. Still need human review
5. Can be overconfident (like me making mistakes!)
```

**My approach:** AI generates → I verify → I test → I own the code.

---

# 📋 SYSTEM DESIGN & SCALABILITY (Q31-Q36)

## **Q31: How do you ensure fault tolerance for 1M+ transactions?**

### **Honest Answer:**

```
1. Stateless services (can restart without data loss)
2. Database persistence before acknowledging
3. Retry mechanisms for transient failures
4. Redis cluster for cache HA
5. Multiple service instances behind load balancer
```

**What we DON'T have:**
- Circuit breaker (not implemented)
- Chaos engineering
- Multi-region deployment

---

## **Q32: How do you handle database scalability?**

### **Honest Answer:**

```
1. Read replicas for read-heavy queries
2. Connection pooling (HikariCP)
3. Query optimization (indexes, explain plans)
4. Batch processing for bulk operations
```

**What we DON'T have:**
- Database sharding
- Horizontal partitioning
- Automated scaling

---

## **Q33: Explain your Kafka usage**

### **Honest Answer:**

> "**We don't use Kafka in our current architecture.**"

We use:
- `CompletableFuture` + `ThreadPoolTaskExecutor` for async processing
- Cron jobs for retry
- Direct HTTP calls between services

**Why no Kafka:**
> "Current scale doesn't require it. Operational complexity not justified."

---

## **Q34: How did you prioritize partner integrations?**

### **Honest Answer:**

```
1. Business revenue impact (₹/month potential)
2. Technical complexity (API readiness)
3. Partner timeline requirements
4. Team bandwidth
```

**Data-driven:** Product team provided revenue projections per partner.

---

## **Q35: Tell me about a time state machine didn't work as expected**

### **Honest Answer:**

> "The GPay cache race condition. Status was set correctly but validation failed due to stale cache."

**How I handled:**
1. Reproduced timing issue
2. Identified cache as culprit
3. Implemented bypass cache + retry
4. Added logging to track retry attempts

---

## **Q36: How do you measure 30% reduction in onboarding time?**

### **Honest Answer:**

> "I'd need to verify exact methodology. Likely measured as:
> - Before: X days to onboard partner
> - After (with state machine): Y days
> - Reduction = (X-Y)/X"

**What helped:**
- Reusable stage configurations
- Partner template configs
- ConfigNexus for config management

---

# 📋 TRADE-OFFS & DECISIONS (Q37-Q39)

## **Q37: Why microservices for NACH and InsureX instead of monolith?**

### **Honest Answer:**

```
1. Independent deployment (don't deploy entire monolith for NACH change)
2. Team autonomy (NACH team owns NACH service)
3. Different scaling needs
4. Technology flexibility
```

**Trade-off:**
> "More operational complexity. For smaller team, monolith might be simpler."

---

## **Q38: When do you choose Kafka vs Webhooks?**

### **Honest Answer:**

```
Webhooks (what we use):
- Partner-initiated callbacks
- Simpler to implement
- Direct acknowledgment
- No infrastructure overhead

Kafka (what we'd use for):
- High-volume internal events
- Decoupling services
- Event replay needed
- At-least-once guaranteed
```

**Our choice:** Webhooks for partner callbacks. No internal Kafka.

---

## **Q39: How do you balance speed vs quality?**

### **Honest Answer:**

```
1. Critical paths (money flows): Quality first, thorough testing
2. Non-critical features: Ship faster, iterate
3. Tech debt: Track but don't block releases
4. Code review: Always, but with clear scope
```

**Example:** Meesho factory pattern - I pushed for quality, but offered to absorb extra time.

---

# 📋 FAILURE SCENARIOS (Q40-Q42)

## **Q40: What if NACH mandate succeeds but webhook fails?**

### **Honest Answer:**

```
1. Mandate created at Digio → Status = SUCCESS
2. Partner webhook fails → WebhookStatus = FAILED, retryRequired = true
3. Cron picks up for retry
4. If persistent → Manual intervention via admin dashboard
```

**No automated reconciliation.** Alert on persistent failures.

---

## **Q41: How do you handle partial failures in batch processing?**

### **Honest Answer:**

```java
for (Record record : batch) {
    try {
        process(record);
        successCount++;
    } catch (Exception e) {
        log.error("Failed to process record: {}", record.getId(), e);
        failedRecords.add(record);
    }
}
// Continue with rest, don't rollback successful ones
```

**No transaction rollback** for partial failures. Process what we can, log failures.

---

## **Q42: What if state machine service goes down mid-transition?**

### **Honest Answer:**

```
1. Stage A inserted → saved to DB (committed)
2. Service crashes before Stage B trigger completes
3. On restart:
   - Application shows Stage A as last active
   - Cron detects "stuck" applications
   - Retry trigger

No automatic recovery. Manual/cron-based detection.
```

---

# 📋 METRICS & MONITORING (Q43-Q45)

## **Q43: How do you measure 20% improvement in webhook reliability?**

### **Honest Answer:**

> "I'd need to verify measurement methodology."

**What we track:**
- Webhook success rate (SUCCESS/TOTAL)
- Retry count per webhook
- Time to successful delivery

---

## **Q44: What monitoring do you have in place?**

### **Honest Answer:**

```
1. SigNoz: APM, distributed tracing
2. Kibana/ELK: Log aggregation, search
3. Coralogix: Log analytics, alerts
4. Grafana: Custom dashboards
5. Sentry: Error tracking
6. PagerDuty: On-call alerting
```

---

## **Q45: How do you debug production issues in distributed systems?**

### **Honest Answer:**

```
1. Correlation ID (MDC) for tracing across services
2. Structured logs with application_id
3. SigNoz traces for latency breakdown
4. Kibana for log search
5. Redash for database queries
```

**Example:** GPay cache issue - traced via correlation ID, found timing gap in logs.

---

# 📋 SECURITY & COMPLIANCE (Q46-Q47)

## **Q46: How do you ensure PCI-DSS compliance?**

### **Honest Answer:**

> "We don't store card data. PCI-DSS not applicable for our flows."

**What we do:**
- PAN encrypted (CryptoUtility)
- Aadhaar tokenized (via UIDAI APIs)
- Audit logging for sensitive changes

---

## **Q47: How do you handle sensitive data (PAN, Aadhaar)?**

### **Honest Answer:**

```java
// From: CryptoUtility.java
String encryptedPan = CryptoUtility.encryptHandlesNull(tenantId, panNumber.toUpperCase());
```

**What we encrypt:**
- PAN numbers
- Bank account numbers  
- Aadhaar numbers
- Addresses (line1, line2)
- DOB
- Phone numbers
- Email addresses

**What we don't encrypt:**
- Names (stored in plaintext)
- Application IDs
- City, State, Pincode

---

# 📋 QUESTIONS FOR INTERVIEWER

**Strategic Questions to Ask:**

1. **"How does Tide handle NACH mandates?"**
2. **"What's your partner integration approach?"**
3. **"How do you balance feature velocity with compliance?"**
4. **"What's your observability stack?"**
5. **"What does success look like in 90 days?"**

---

# 🎯 QUICK REFERENCE

## **SAY:**
✅ "We use Factory pattern for NACH types"
✅ "We use history-based state tracking, not a formal library"
✅ "We rely on idempotent retries, not Saga-style compensation"
✅ "We had a cache race condition - taught us to bypass cache for critical validations"
✅ "I'd need to verify the exact measurement for that metric"

## **DON'T SAY:**
❌ "We have exactly-once delivery" (we have at-least-once)
❌ "We use Saga with compensation" (we use retries)
❌ "We have automated reconciliation" (manual + cron)
❌ "We use Kafka" (we don't)
❌ Fabricate metrics you can't explain

---

**Remember: Honesty + Technical Depth > Impressive-sounding claims** 🚀
