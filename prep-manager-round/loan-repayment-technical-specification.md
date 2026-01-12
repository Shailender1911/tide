# 🏦 Loan Repayment Service - Complete Technical Deep Dive

**Comprehensive Preparation Guide for Hiring Manager Round - Tide Interview**

---

## 📋 Table of Contents

1. [Microservices vs Monolith Architecture](#microservices-vs-monolith-architecture)
2. [Load Characteristics & Performance](#load-characteristics--performance)
3. [Pros & Cons Analysis](#pros--cons-analysis)
4. [Reliability & Observability](#reliability--observability)
5. [Production Debugging & Issue Management](#production-debugging--issue-management)
6. [Monitoring, Alerting & Tracing](#monitoring-alerting--tracing)
7. [Failure Recovery Mechanisms](#failure-recovery-mechanisms)
8. [Current Architecture Deep Dive](#current-architecture-deep-dive)
9. [Behavioral Questions](#behavioral-questions)
10. [Key Talking Points Summary](#key-talking-points-summary)

---

## 🏗️ Microservices vs Monolith Architecture

### **Why Loan Repayment is Microservices**

```
┌────────────────────────────────────────────────────────────┐
│  LOAN REPAYMENT SERVICE (Microservices)                    │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  Architecture Decision: Microservices                      │
│  ├── Independent Deployment                                │
│  ├── Technology Flexibility                                │
│  ├── Scalability per Component                             │
│  ├── Fault Isolation                                       │
│  └── Team Autonomy                                          │
│                                                              │
│  Key Characteristics:                                       │
│  ├── Port: 8078 (dedicated)                                │
│  ├── Database: Separate (loan_repayment DB)                │
│  ├── Deployment: Independent                               │
│  ├── Scaling: Horizontal (per service)                    │
│  └── Team: Dedicated team                                  │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

**Reasons for Microservices:**

1. **Independent Scaling**: Repayment processing has different load patterns than application processing
   - Repayment peaks: Daily 9 AM - 9 PM (repayment cutoff)
   - Application peaks: Throughout day (user-initiated)
   - Can scale repayment service independently during peak hours

2. **Technology Flexibility**: Different optimization needs
   - Loan Repayment: Heavy async processing, thread pools, read-write DB separation
   - ZipCredit: Rule engine (Drools), complex business logic, synchronous processing

3. **Fault Isolation**: Payment failures shouldn't impact application processing
   - If repayment service fails, applications can still be created
   - If ZipCredit fails, repayments can still be processed

4. **Team Autonomy**: Different teams own different services
   - Repayment team: Payment processing, settlement, LMS integration
   - ZipCredit team: Application processing, rules engine, underwriting

5. **Deployment Independence**: 
   - Repayment changes don't require ZipCredit deployment
   - Faster release cycles for repayment-specific features

### **Why ZipCredit is Monolith**

```
┌────────────────────────────────────────────────────────────┐
│  ZIPCREDIT SERVICE (Monolith)                              │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  Architecture Decision: Monolith                           │
│  ├── Tight Coupling Required                               │
│  ├── Shared Database                                        │
│  ├── Complex Business Rules                                 │
│  ├── Transaction Consistency                                │
│  └── Legacy System Evolution                                │
│                                                              │
│  Key Characteristics:                                       │
│  ├── Port: 8080 (default)                                   │
│  ├── Database: Shared (dgl_database)                       │
│  ├── Modules: 20+ tightly coupled modules                  │
│  ├── Deployment: Single WAR file                           │
│  └── Team: Single team owns entire system                 │
│                                                              │
│  Modules:                                                   │
│  ├── dgl-services (core application logic)                 │
│  ├── dgl-ruleEngine (Drools BRE)                           │
│  ├── dgl-connectors (external integrations)                │
│  ├── dgl-documentServices (document management)           │
│  ├── dgl-adminServices (admin operations)                  │
│  └── dglSchedulers (cron jobs)                            │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

**Reasons for Monolith:**

1. **Tight Business Logic Coupling**: 
   - Application creation → Rules evaluation → Document generation → KYC → Disbursement
   - All steps need ACID transactions across multiple tables
   - Breaking this into microservices would require distributed transactions (Saga pattern complexity)

2. **Shared Database Requirements**:
   - Application data, rules results, documents, KYC data all in same database
   - Complex joins across tables for eligibility checks
   - Breaking would require data duplication or complex data synchronization

3. **Legacy System Evolution**:
   - Started as monolith 4+ years ago
   - Gradual evolution rather than rewrite
   - Migration to microservices would be high-risk, high-cost

4. **Rule Engine Integration**:
   - Drools rule engine tightly integrated with application flow
   - Rules reference multiple domain objects
   - Microservices would require complex rule distribution

5. **Transaction Consistency**:
   - Application state changes need immediate consistency
   - Cannot tolerate eventual consistency for application status
   - Monolith provides ACID guarantees

### **Architecture Comparison**

| Aspect | Loan Repayment (Microservices) | ZipCredit (Monolith) |
|--------|-------------------------------|----------------------|
| **Deployment** | Independent (port 8078) | Single WAR (port 8080) |
| **Database** | Separate (loan_repayment) | Shared (dgl_database) |
| **Scaling** | Horizontal per service | Vertical (single instance) |
| **Technology** | Spring Boot 3, Java 17 | Spring Boot 2, Java 11 |
| **Team Structure** | Dedicated repayment team | Single ZipCredit team |
| **Release Cycle** | Independent (weekly) | Coordinated (bi-weekly) |
| **Fault Isolation** | High (service-level) | Low (module-level) |
| **Complexity** | Distributed system complexity | Monolithic complexity |
| **Performance** | Optimized for async processing | Optimized for synchronous flow |
| **Use Case** | Post-disbursement operations | Pre-disbursement operations |

### **Trade-offs Analysis**

#### **Microservices (Loan Repayment) - Pros**

✅ **Independent Scaling**
- Scale repayment service during peak hours (9 AM - 9 PM)
- Don't need to scale entire system

✅ **Technology Flexibility**
- Can use latest Spring Boot 3, Java 17
- Optimized thread pools, async processing
- Read-write database separation

✅ **Fault Isolation**
- Repayment failures don't impact application creation
- Can deploy fixes independently

✅ **Team Autonomy**
- Repayment team can deploy without coordination
- Faster feature development

✅ **Performance Optimization**
- Service-specific optimizations (async, thread pools)
- Database optimizations (read replicas)

#### **Microservices (Loan Repayment) - Cons**

❌ **Distributed System Complexity**
- Network latency between services
- Need API versioning
- Service discovery required

❌ **Data Consistency Challenges**
- Eventual consistency between services
- Need distributed transaction patterns
- Data synchronization complexity

❌ **Operational Overhead**
- Multiple deployments to manage
- Service monitoring across services
- Debugging across service boundaries

❌ **Network Failures**
- Dependency on network calls
- Need retry mechanisms
- Circuit breakers required

#### **Monolith (ZipCredit) - Pros**

✅ **Simpler Development**
- Single codebase
- Easier debugging
- Shared memory for caching

✅ **ACID Transactions**
- Strong consistency guarantees
- No distributed transaction complexity
- Simpler error handling

✅ **Performance (for synchronous flows)**
- No network latency
- In-memory processing
- Single database queries

✅ **Easier Testing**
- Single deployment to test
- End-to-end testing simpler
- No service mocking needed

#### **Monolith (ZipCredit) - Cons**

❌ **Scaling Challenges**
- Must scale entire system
- Cannot scale individual components
- Resource waste

❌ **Technology Lock-in**
- Harder to adopt new technologies
- Stuck with older Spring Boot 2, Java 11
- Migration requires full system upgrade

❌ **Deployment Risk**
- Single deployment affects entire system
- Higher risk of breaking changes
- Requires coordination across teams

❌ **Fault Propagation**
- One module failure can affect entire system
- Harder to isolate issues
- Single point of failure

### **Converting Cons to Pros**

#### **Loan Repayment Microservices - Converting Cons**

**Challenge: Distributed System Complexity**
- **Solution**: 
  - Use Spring Cloud for service discovery
  - Implement API versioning (`/api/v1/`, `/api/v2/`)
  - Use correlation IDs for request tracing
  - **Result**: Managed complexity with proper tooling

**Challenge: Data Consistency**
- **Solution**:
  - Use eventual consistency where acceptable (webhooks, async processing)
  - Implement idempotency keys for critical operations
  - Use database transactions within service boundaries
  - **Result**: Acceptable consistency with performance benefits

**Challenge: Operational Overhead**
- **Solution**:
  - Automated CI/CD pipelines
  - Centralized logging (ELK stack)
  - Distributed tracing (Micrometer, Zipkin)
  - Health checks and monitoring
  - **Result**: Automation reduces overhead

**Challenge: Network Failures**
- **Solution**:
  - Retry mechanisms with exponential backoff
  - Circuit breaker pattern (planned)
  - Async processing with queues
  - Webhook retry with database persistence
  - **Result**: Resilient to network failures

#### **ZipCredit Monolith - Converting Cons**

**Challenge: Scaling Challenges**
- **Solution**:
  - Read-write database separation (within monolith)
  - Connection pooling (HikariCP)
  - Caching (Redis) for frequently accessed data
  - Async processing for non-critical operations
  - **Result**: Better resource utilization

**Challenge: Technology Lock-in**
- **Solution**:
  - Gradual migration strategy
  - Extract microservices for new features (Loan Repayment, Orchestration)
  - Keep core monolith stable
  - **Result**: Hybrid approach - stability + innovation

**Challenge: Deployment Risk**
- **Solution**:
  - Feature flags for gradual rollout
  - Blue-green deployment strategy
  - Comprehensive testing (unit, integration, E2E)
  - Database migration scripts
  - **Result**: Reduced deployment risk

**Challenge: Fault Propagation**
- **Solution**:
  - Modular architecture within monolith
  - Exception handling at module boundaries
  - Health checks per module
  - Circuit breaker pattern for external calls
  - **Result**: Better fault isolation

---

## 📊 Load Characteristics & Performance

### **Loan Repayment Service Load**

#### **Traffic Patterns**

```
┌────────────────────────────────────────────────────────────┐
│  DAILY TRAFFIC PATTERNS                                    │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  Peak Hours: 9:00 AM - 9:00 PM (Repayment Cutoff)         │
│  ├── VA Deposit Webhooks: 500-1000/hour                   │
│  ├── Repayment Processing: 200-500/hour                   │
│  ├── Settlement Processing: 50-100/hour                   │
│  └── LMS Integration: 300-600/hour                         │
│                                                              │
│  Off-Peak Hours: 9:00 PM - 9:00 AM                         │
│  ├── VA Deposit Webhooks: 50-100/hour                      │
│  ├── Repayment Processing: 10-20/hour                     │
│  └── Background Jobs: Cron-based                           │
│                                                              │
│  Monthly Volume:                                           │
│  ├── Total Repayments: 10,000-15,000/month                 │
│  ├── Total Settlements: 2,000-3,000/month                 │
│  ├── VA Deposits: 50,000-75,000/month                      │
│  └── LMS Transactions: 20,000-30,000/month                │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

#### **Performance Metrics**

| Metric | Target | Actual | Notes |
|--------|--------|--------|-------|
| **API Response Time (P95)** | < 500ms | 300-400ms | Async processing |
| **API Response Time (P99)** | < 1s | 600-800ms | Includes external calls |
| **Throughput** | 100 TPS | 150-200 TPS | Peak hours |
| **Database Query Time** | < 50ms | 10-30ms | Read-write separation |
| **External API Calls** | < 2s | 1-1.5s | LMS, Payout, ENACH |
| **Webhook Processing** | < 5s | 2-4s | Async with retry |
| **Memory Usage** | < 2GB | 1.5-1.8GB | JVM heap |
| **CPU Usage** | < 70% | 50-60% | Average load |

#### **Load Testing Results**

```bash
# Load Test Configuration
- Tool: Apache JMeter / Custom scripts
- Concurrent Users: 100
- Duration: 30 minutes
- Ramp-up: 10 users/second

# Results:
Average Response Time: 350ms
P50 (Median): 280ms
P90: 450ms
P95: 520ms
P99: 780ms
Throughput: 180 TPS
Error Rate: < 0.1%
```

#### **Bottlenecks Identified & Resolved**

1. **Database Query Performance**
   - **Before**: 200-300ms average query time
   - **After**: 10-30ms (read-write separation)
   - **Improvement**: 10x faster

2. **External API Calls**
   - **Before**: Synchronous blocking (5-10s response time)
   - **After**: Async processing (200-500ms response time)
   - **Improvement**: 20x faster API responses

3. **Thread Pool Exhaustion**
   - **Before**: Single thread pool (50 threads)
   - **After**: Dedicated pools (5+ pools, 150+ total threads)
   - **Improvement**: 3x higher concurrent processing

---

## ⚖️ Pros & Cons Analysis

### **Loan Repayment Microservices - Detailed Analysis**

#### **Pros**

1. **Independent Scaling** ⭐⭐⭐⭐⭐
   - Scale repayment service during peak hours
   - Don't need to scale entire ZipCredit system
   - Cost optimization

2. **Technology Flexibility** ⭐⭐⭐⭐⭐
   - Latest Spring Boot 3, Java 17
   - Modern async patterns
   - Read-write database separation

3. **Fault Isolation** ⭐⭐⭐⭐⭐
   - Repayment failures don't impact applications
   - Can deploy fixes independently
   - Better system resilience

4. **Team Autonomy** ⭐⭐⭐⭐
   - Independent development cycles
   - Faster feature delivery
   - Reduced coordination overhead

5. **Performance Optimization** ⭐⭐⭐⭐⭐
   - Service-specific optimizations
   - Async processing
   - Database read replicas

#### **Cons**

1. **Distributed System Complexity** ⭐⭐⭐
   - Network latency
   - Service discovery
   - API versioning

2. **Data Consistency** ⭐⭐⭐
   - Eventual consistency challenges
   - Distributed transaction complexity

3. **Operational Overhead** ⭐⭐⭐
   - Multiple deployments
   - Service monitoring
   - Debugging complexity

4. **Network Failures** ⭐⭐⭐⭐
   - Dependency on network
   - Need retry mechanisms
   - Circuit breakers required

### **ZipCredit Monolith - Detailed Analysis**

#### **Pros**

1. **Simpler Development** ⭐⭐⭐⭐
   - Single codebase
   - Easier debugging
   - Shared memory

2. **ACID Transactions** ⭐⭐⭐⭐⭐
   - Strong consistency
   - No distributed transactions
   - Simpler error handling

3. **Performance (Synchronous)** ⭐⭐⭐⭐
   - No network latency
   - In-memory processing
   - Single database queries

4. **Easier Testing** ⭐⭐⭐⭐
   - Single deployment
   - End-to-end testing
   - No service mocking

#### **Cons**

1. **Scaling Challenges** ⭐⭐⭐
   - Must scale entire system
   - Resource waste
   - Cannot scale components independently

2. **Technology Lock-in** ⭐⭐⭐
   - Older Spring Boot 2, Java 11
   - Harder to adopt new tech
   - Migration complexity

3. **Deployment Risk** ⭐⭐⭐⭐
   - Single deployment affects all
   - Higher risk
   - Requires coordination

4. **Fault Propagation** ⭐⭐⭐
   - One failure affects all
   - Harder to isolate
   - Single point of failure

---

## 🔒 Reliability & Observability

### **Reliability Mechanisms**

#### **1. Idempotency**

```java
// Idempotency Key Pattern
@PostMapping("/repayment")
public ResponseEntity<RepaymentResponse> processRepayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody RepaymentRequest request) {
    
    // Check if already processed
    Optional<Repayment> existing = repaymentRepository
        .findByIdempotencyKey(idempotencyKey);
    
    if (existing.isPresent()) {
        return ResponseEntity.ok(convertToResponse(existing.get()));
    }
    
    // Process new request
    Repayment repayment = repaymentService.process(request);
    repayment.setIdempotencyKey(idempotencyKey);
    repaymentRepository.save(repayment);
    
    return ResponseEntity.ok(convertToResponse(repayment));
}
```

**Benefits:**
- Prevents duplicate processing
- Safe retry mechanism
- Database unique constraint on idempotency_key

#### **2. Transaction Management**

```java
@Transactional
public void processRepayment(RepaymentRequest request) {
    // 1. Create repayment record
    Repayment repayment = createRepayment(request);
    
    // 2. Update loan status
    loan.setStatus(LoanStatus.REPAYMENT_IN_PROGRESS);
    loanRepository.save(loan);
    
    // 3. Create settlement record
    MerchantSettlement settlement = createSettlement(request);
    merchantSettlementRepository.save(settlement);
    
    // All-or-nothing: If any step fails, entire transaction rolls back
}
```

**Benefits:**
- ACID guarantees
- Data consistency
- Rollback on failures

#### **3. Async Processing with Persistence**

```java
@Async("payoutThreadPoolExecutor")
public void processPayoutAsync(PayoutRequest request) {
    try {
        // Process payout
        processPayout(request);
    } catch (Exception e) {
        // Save to database for retry
        Webhook webhook = Webhook.builder()
            .externalId(request.getTransferId())
            .status(WebhookStatus.FAILED)
            .shouldRetry(true)
            .retryCount(0)
            .payload(objectMapper.writeValueAsString(request))
            .build();
        webhookRepository.save(webhook);
        
        // Cron job will retry later
    }
}
```

**Benefits:**
- Non-blocking API responses
- Guaranteed processing
- Retry mechanism

### **Observability Stack**

#### **1. Logging**

```java
@Slf4j
@Service
public class RepaymentServiceImpl {
    
    public void processRepayment(RepaymentRequest request) {
        // Structured logging with correlation ID
        MDC.put("correlationId", request.getCorrelationId());
        MDC.put("applicationId", request.getApplicationId());
        
        log.info("Processing repayment for application: {}", 
            request.getApplicationId());
        
        try {
            // Business logic
            processRepaymentLogic(request);
            
            log.info("Repayment processed successfully: {}", 
                request.getApplicationId());
        } catch (Exception e) {
            log.error("Repayment processing failed: {}", 
                request.getApplicationId(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}
```

**Log Levels:**
- **ERROR**: Exceptions, failures
- **WARN**: Retries, fallbacks
- **INFO**: Business events, state changes
- **DEBUG**: Detailed flow, data

**Log Format:**
```
2024-01-15 10:30:45.123 INFO  [LoanRepaymentService,abc123,def456] Processing repayment for application: APP12345
```

#### **2. Metrics (Micrometer)**

```java
@Service
public class RepaymentServiceImpl {
    
    private final Counter repaymentCounter;
    private final Timer repaymentTimer;
    private final Gauge activeRepayments;
    
    public RepaymentServiceImpl(MeterRegistry meterRegistry) {
        this.repaymentCounter = Counter.builder("repayment.total")
            .description("Total repayments processed")
            .tag("status", "success")
            .register(meterRegistry);
        
        this.repaymentTimer = Timer.builder("repayment.duration")
            .description("Repayment processing time")
            .register(meterRegistry);
    }
    
    public void processRepayment(RepaymentRequest request) {
        Timer.Sample sample = Timer.start();
        
        try {
            processRepaymentLogic(request);
            repaymentCounter.increment("status", "success");
        } catch (Exception e) {
            repaymentCounter.increment("status", "failure");
            throw e;
        } finally {
            sample.stop(repaymentTimer);
        }
    }
}
```

**Key Metrics:**
- `repayment.total` - Total repayments processed
- `repayment.duration` - Processing time
- `repayment.failure` - Failure count
- `webhook.retry.count` - Retry attempts
- `lms.api.duration` - LMS API call time
- `database.query.duration` - Database query time

#### **3. Distributed Tracing**

```properties
# application.properties
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=W3C

logging.pattern.level=%5p [${spring.application.name:},%X{trace_id:-},%X{span_id:-}]
```

**Trace Flow:**
```
Request → Orchestration (trace_id: abc123)
    ↓
Loan Repayment Service (trace_id: abc123, span_id: def456)
    ↓
LMS API Call (trace_id: abc123, span_id: ghi789)
    ↓
Payout Service (trace_id: abc123, span_id: jkl012)
```

**Benefits:**
- End-to-end request tracking
- Performance bottleneck identification
- Service dependency mapping

#### **4. Health Checks**

```java
@Component
public class RepaymentHealthIndicator implements HealthIndicator {
    
    @Autowired
    private LoanRepository loanRepository;
    
    @Autowired
    private FinfluxClient finfluxClient;
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        // Database health
        try {
            loanRepository.count();
            builder.up().withDetail("database", "UP");
        } catch (Exception e) {
            builder.down().withDetail("database", "DOWN: " + e.getMessage());
        }
        
        // LMS health
        try {
            finfluxClient.healthCheck();
            builder.up().withDetail("lms", "UP");
        } catch (Exception e) {
            builder.down().withDetail("lms", "DOWN: " + e.getMessage());
        }
        
        return builder.build();
    }
}
```

**Health Endpoints:**
- `/actuator/health` - Overall health
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe

---

## 🐛 Production Debugging & Issue Management

### **Production Debugging Workflow**

#### **Step 1: Issue Identification**

```markdown
**Issue Report Format:**
- Application ID: APP12345
- Loan ID: LOAN67890
- Transaction ID: TXN12345
- Error Message: "Repayment processing failed"
- Timestamp: 2024-01-15 10:30:45
- Service: loan-repayment
```

#### **Step 2: Database Investigation**

```sql
-- 1. Check application status
SELECT application_id, status, created_at, updated_at
FROM a_user_application
WHERE application_id = 'APP12345';

-- 2. Check loan details
SELECT id, application_id, status, disbursal_date, loan_amount
FROM loan
WHERE application_id = 'APP12345';

-- 3. Check repayment attempts
SELECT id, loan_id, amount, status, created_at, error_message
FROM loan_payment
WHERE loan_id = 'LOAN67890'
ORDER BY created_at DESC;

-- 4. Check webhook status
SELECT id, external_id, status, retry_count, should_retry, payload, created_at
FROM webhook
WHERE external_id = 'TXN12345'
ORDER BY created_at DESC;

-- 5. Check settlement records
SELECT id, application_id, amount, status, created_at
FROM merchant_settlement
WHERE application_id = 'APP12345';
```

#### **Step 3: Log Analysis**

```bash
# Connect to production log server
ssh -A 10.165.10.29 -p 33000 "ssh 10.165.10.190 -p 33000"

# Search logs for application ID
grep -n "APP12345" /logs/loanrepayment/loanrepayment-2024-01-15.log

# Search for errors
grep -n "ERROR\|Exception\|FATAL" /logs/loanrepayment/loanrepayment-2024-01-15.log | grep -A 10 -B 10 "APP12345"

# Search for specific transaction
grep -n "TXN12345" /logs/loanrepayment/loanrepayment-2024-01-15.log
```

#### **Step 4: Root Cause Analysis**

**Common Issues & Solutions:**

1. **LMS API Failure**
   - **Symptom**: Repayment status stuck in "IN_PROGRESS"
   - **Root Cause**: LMS API timeout or failure
   - **Solution**: Check LMS health, retry mechanism, async processing

2. **Duplicate Payment**
   - **Symptom**: "Duplicate payment detected" error
   - **Root Cause**: Idempotency key missing or duplicate
   - **Solution**: Check idempotency key, database unique constraint

3. **Insufficient Balance**
   - **Symptom**: "Insufficient VA balance" error
   - **Root Cause**: Merchant doesn't have enough balance
   - **Solution**: Check VA balance, split payment logic

4. **Webhook Retry Exhausted**
   - **Symptom**: Webhook status "FAILED", retry_count = 3
   - **Root Cause**: External service down or invalid payload
   - **Solution**: Check external service health, validate payload

#### **Step 5: Resolution & Prevention**

```markdown
**Resolution Steps:**
1. Immediate Fix: Manual intervention if needed
2. Code Fix: Fix root cause in code
3. Testing: Verify fix in staging
4. Deployment: Deploy to production
5. Monitoring: Monitor for recurrence

**Prevention Measures:**
1. Add better error handling
2. Improve logging
3. Add alerts for similar issues
4. Update runbooks
5. Team knowledge sharing
```

### **Issue Management Process**

#### **1. Severity Classification**

| Severity | Impact | Response Time | Example |
|----------|--------|---------------|---------|
| **P0 - Critical** | System down, data loss | 15 minutes | Payment processing completely down |
| **P1 - High** | Major feature broken | 1 hour | Repayment processing failing for specific partner |
| **P2 - Medium** | Minor feature broken | 4 hours | Webhook retry failing occasionally |
| **P3 - Low** | Cosmetic issue | 1 day | Log format issue |

#### **2. Escalation Path**

```
Developer → Tech Lead → Engineering Manager → CTO
```

#### **3. Post-Mortem Process**

```markdown
**Post-Mortem Template:**
1. Issue Summary
2. Timeline
3. Root Cause
4. Impact Assessment
5. Resolution Steps
6. Prevention Measures
7. Action Items
```

---

## 📈 Monitoring, Alerting & Tracing

### **Three-Tier Observability Stack**

We use a comprehensive observability architecture with three complementary tools:

```
┌────────────────────────────────────────────────────────────┐
│  PRODUCTION OBSERVABILITY STACK                             │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Kibana (ELK Stack)                                      │
│     URL: payufin-prod-kibana.payufin.io                    │
│     ├── 29+ million log hits daily                         │
│     ├── Kubernetes pod logs                                │
│     ├── KQL-based search and filtering                      │
│     └── Real-time log streaming                            │
│                                                              │
│  2. SigNoz (Distributed Tracing)                            │
│     URL: payuwibmo-signoz.payufin.in                       │
│     ├── End-to-end request tracing                          │
│     ├── Service dependency mapping                          │
│     ├── Performance bottleneck identification               │
│     └── Error rate tracking (2% error rate)                 │
│                                                              │
│  3. Coralogix (APM & Analytics)                            │
│     URL: payu-apm.app.coralogix.in                          │
│     ├── 10.45M traces in 15 minutes                        │
│     ├── AI-powered anomaly detection                        │
│     ├── Service-level performance metrics                   │
│     └── Advanced error pattern analysis                    │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### **1. Kibana (ELK Stack) - Log Analysis**

**Production URL**: [https://payufin-prod-kibana.payufin.io/app/discover](https://payufin-prod-kibana.payufin.io/app/discover)

#### **Configuration**

- **Index Pattern**: `prod-k8s-logs-ams-nbfc-server`
- **Log Volume**: 29,431,466 hits in 24 hours
- **Time Range**: Real-time + historical (30 days retention)
- **Collection**: Kubernetes pod logs via Filebeat/Logstash

#### **Key Capabilities**

**Log Search & Filtering:**
```kql
# Production debugging queries
application_id: "APP12345" AND level: "ERROR"
duration: >1000 AND operation: "SELECT"
message: "webhook" AND status: "FAILED"
kubernetes.deployment.name: "loan-repayment"
```

**Use Cases:**
- Production issue debugging by application_id/transaction_id
- Error pattern analysis
- Traffic pattern visualization
- Container-level log analysis

**Real Example:**
> "When debugging a repayment failure, I search Kibana:
> 1. Filter: `application_id: "APP12345"` AND `@timestamp: [last 1 hour]`
> 2. Filter: `level: "ERROR"` or `level: "WARN"`
> 3. Analyze: Error messages, stack traces, request context
> 4. Trace: Correlation IDs across services
> 
> This gives me complete context of what happened, when, and why."

### **2. SigNoz - Distributed Tracing**

**Production URL**: [https://payuwibmo-signoz.payufin.in/trace](https://payuwibmo-signoz.payufin.in/trace)

#### **Services Monitored**

- **prod-lrs** (Loan Repayment Service)
- **prod-orch** (Orchestration Service)
- **prod-zc** (ZipCredit Service)
- **Prod_Lending**, **Prod_Neo-admin**, **Prod_Neo-merchant**
- **mysql**, **redis**, **route**, **customer**, **driver**

#### **Key Metrics**

- **Total Traces**: 1M+ in time window
- **Error Rate**: 22,553 errors / 1,070,487 successful (~2%)
- **Duration Range**: 0ms to 343s
- **Service Dependencies**: Visual service map

#### **Trace Examples**

```
Example 1: Successful Request
prod-orch → GET /orchestration/v1/gpay/application/status
Duration: 64.34 ms
Status: ok

Example 2: Database Query
prod-lrs → SELECT loan_repayment.payout_credit_info
Duration: 0.84 ms
Status: ok

Example 3: Repository Call
prod-lrs → PayoutCreditInfoRepository.findByApplicationIdAndExternalReferenceNumber
Duration: 1.00 ms
Status: ok
```

#### **Use Cases**

- **Performance Analysis**: Identify slow operations
- **Error Tracking**: Filter by `status: error`
- **Service Dependencies**: Understand microservice interactions
- **Bottleneck Identification**: Find slowest operations

**Real Example:**
> "SigNoz helped identify a performance bottleneck:
> - Found: Database query taking 5 seconds
> - Service: prod-lrs
> - Operation: SELECT loan_repayment.payout_credit_info
> - Root Cause: Missing index on application_id
> - Fix: Added index, query time reduced to 0.84ms"

### **3. Coralogix - APM & Advanced Analytics**

**Production URL**: [https://payu-apm.app.coralogix.in/#/query-new/tracing](https://payu-apm.app.coralogix.in/#/query-new/tracing)

#### **Scale & Metrics**

- **Trace Volume**: 10.45 million traces in 15 minutes
- **Applications**: 
  - default: 9.73M traces
  - smb-lending: 614K traces
  - otel: 103K traces

#### **Performance Graphs**

**1. Max Duration by Action:**
- POST Execute prepar...: 415s (peak)
- POST /v0/bbps...: 200s
- SELECT L:11 4-: 100s

**2. Spans per Service:**
- billpayments: 138K spans (peak)
- shylock_prod: 80K spans
- webapp-gravit: 50K spans

**3. Errors per Service:**
- secureapp-gra: 4.19K errors (peak)
- webapp-gravit: 2K errors
- sauron_gravito: 1.5K errors

#### **Advanced Features**

- **AI-Powered Anomaly Detection**: Automatic issue identification
- **Latency Percentile Analysis**: P50, P95, P99 tracking
- **Error Pattern Analysis**: Group errors by type
- **Service Health Monitoring**: Real-time service status

**Real Example:**
> "Coralogix alerted us to a production issue:
> - Alert: Error spike in secureapp-gravit (4.19K errors)
> - Time: Peak hours (23:35)
> - Correlation: Performance degradation at same time
> - Root Cause: Database connection pool exhaustion
> - Solution: Increased pool size, added read replicas
> - Result: Error rate dropped, performance improved"

### **Integrated Debugging Workflow**

```
┌────────────────────────────────────────────────────────────┐
│  PRODUCTION DEBUGGING WORKFLOW                              │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Alert Triggered                                         │
│     ↓ Coralogix: Error rate spike detected                  │
│                                                              │
│  2. Identify Affected Service                               │
│     ↓ Coralogix: prod-lrs service showing errors           │
│                                                              │
│  3. Trace Request Flow                                      │
│     ↓ SigNoz: Filter by service, status=error              │
│     ↓ Identify: Operation failing, duration                │
│                                                              │
│  4. Detailed Log Analysis                                   │
│     ↓ Kibana: Search by application_id, error level        │
│     ↓ Analyze: Error messages, stack traces                │
│                                                              │
│  5. Root Cause Identified                                   │
│     ↓ Example: LMS API timeout                             │
│                                                              │
│  6. Fix & Verify                                            │
│     ↓ Implement: Retry mechanism                           │
│     ↓ Monitor: All three tools for verification            │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### **Monitoring Stack**

#### **1. Application Metrics (Prometheus)**

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'loan-repayment'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['loan-repayment:8078']
```

**Key Metrics:**
- `http_server_requests_seconds` - HTTP request duration
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_gc_pause_seconds` - GC pause time
- `process_cpu_usage` - CPU usage
- `database_connections_active` - Active DB connections
- `thread_pool_active_threads` - Active threads

#### **2. Grafana Dashboards**

**Dashboard Panels:**
1. **API Performance**
   - Request rate (requests/second)
   - Response time (P50, P95, P99)
   - Error rate (4xx, 5xx)

2. **Database Performance**
   - Query duration
   - Connection pool usage
   - Read vs Write queries

3. **External API Calls**
   - LMS API response time
   - Payout API response time
   - ENACH API response time

4. **Business Metrics**
   - Repayments processed/hour
   - Settlements created/hour
   - Webhook success rate

5. **System Health**
   - CPU usage
   - Memory usage
   - Thread pool utilization

#### **3. Alerting Rules**

```yaml
# alerting-rules.yml
groups:
  - name: loan-repayment-alerts
    rules:
      # High error rate
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        annotations:
          summary: "High error rate detected"
          
      # High response time
      - alert: HighResponseTime
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 5m
        annotations:
          summary: "P95 response time > 1s"
          
      # Database connection pool exhaustion
      - alert: DatabasePoolExhausted
        expr: database_connections_active / database_connections_max > 0.9
        for: 5m
        annotations:
          summary: "Database connection pool > 90%"
          
      # LMS API failures
      - alert: LMSAPIFailure
        expr: rate(lms_api_calls_total{status="failure"}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "LMS API failure rate > 5%"
```

#### **4. Log Aggregation (ELK Stack)**

**Elasticsearch:**
- Centralized log storage
- Full-text search
- Log retention: 30 days

**Logstash:**
- Log parsing and enrichment
- Field extraction
- Correlation ID injection

**Kibana:**
- Log visualization
- Dashboard creation
- Alert configuration

**Log Queries:**
```json
// Find all errors for specific application
{
  "query": {
    "bool": {
      "must": [
        { "match": { "level": "ERROR" }},
        { "match": { "applicationId": "APP12345" }}
      ]
    }
  }
}

// Find slow API calls
{
  "query": {
    "range": {
      "duration": { "gte": 1000 }
    }
  }
}
```

### **Tracing (Distributed Tracing)**

#### **Implementation**

```java
@RestController
public class RepaymentController {
    
    @Autowired
    private Tracer tracer;
    
    @PostMapping("/repayment")
    public ResponseEntity<RepaymentResponse> processRepayment(
            @RequestBody RepaymentRequest request) {
        
        Span span = tracer.nextSpan()
            .name("processRepayment")
            .tag("applicationId", request.getApplicationId())
            .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            RepaymentResponse response = repaymentService.process(request);
            span.tag("status", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            span.tag("status", "error");
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

#### **Trace Visualization**

**Trace Flow:**
```
[Orchestration] POST /orchestration/repayment
    ↓ (trace_id: abc123)
[Loan Repayment] POST /loan-repayment/repayment
    ├── [Database] SELECT loan WHERE application_id = 'APP12345'
    ├── [LMS] POST /fineract-provider/api/loans/{id}/transactions
    └── [Payout] POST /payout/process
```

**Trace Analysis:**
- Identify slow spans
- Find service dependencies
- Debug distributed issues

---

## 🔄 Failure Recovery Mechanisms

### **Retry Mechanisms**

#### **1. Webhook Retry**

```java
@Entity
public class Webhook {
    @Id
    private Long id;
    private String externalId;
    private WebhookStatus status;
    private Integer retryCount;
    private boolean shouldRetry;
    private String payload;
    private LocalDateTime nextRetryAt;
}

@Service
public class PaymentLinkCronServiceImpl {
    
    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    public Response processWebhooks() {
        List<Webhook> failedWebhooks = webhookRepository
            .findAllByStatusAndShouldRetry(WebhookStatus.FAILED, true);
        
        failedWebhooks.forEach(webhook -> {
            if (shouldRetry(webhook)) {
                try {
                    processWebhook(webhook);
                    webhook.setRetryCount(webhook.getRetryCount() + 1);
                    webhook.setStatus(WebhookStatus.SUCCESS);
                } catch (Exception e) {
                    webhook.setRetryCount(webhook.getRetryCount() + 1);
                    if (webhook.getRetryCount() >= MAX_RETRIES) {
                        webhook.setShouldRetry(false);
                    }
                }
                webhookRepository.save(webhook);
            }
        });
    }
    
    private boolean shouldRetry(Webhook webhook) {
        // Exponential backoff: 15min, 30min, 1hr, 2hr, 4hr
        int[] backoffMinutes = {15, 30, 60, 120, 240};
        if (webhook.getRetryCount() >= backoffMinutes.length) {
            return false;
        }
        
        LocalDateTime nextRetry = webhook.getCreatedAt()
            .plusMinutes(backoffMinutes[webhook.getRetryCount()]);
        
        return LocalDateTime.now().isAfter(nextRetry);
    }
}
```

**Retry Strategy:**
- **Max Retries**: 5 attempts
- **Backoff**: Exponential (15min, 30min, 1hr, 2hr, 4hr)
- **Persistence**: Database-backed
- **Cron**: Every 15 minutes

#### **2. LMS API Retry**

```java
@Service
public class LMSServiceImpl {
    
    @Value("${loanRepayment.finflux.retryCount:3}")
    private int maxRetries;
    
    public GetLoanResponse getLoanDetails(String lmsLoanId) {
        return getLoanDetailsFromFinflux(lmsLoanId, 0);
    }
    
    private GetLoanResponse getLoanDetailsFromFinflux(
            String lmsLoanId, int retryCount) {
        try {
            return finfluxClient.getLoan(lmsLoanId);
        } catch (Exception e) {
            if (retryCount < maxRetries && isRetryableError(e)) {
                log.warn("LMS API call failed, retrying. Attempt: {}/{}", 
                    retryCount + 1, maxRetries);
                
                // Exponential backoff: 1s, 2s, 4s
                sleep((long) Math.pow(2, retryCount) * 1000);
                
                return getLoanDetailsFromFinflux(lmsLoanId, retryCount + 1);
            }
            throw new LMSException("Failed to fetch loan details", e);
        }
    }
    
    private boolean isRetryableError(Exception e) {
        // Retry on network errors, timeouts, 5xx errors
        return e instanceof SocketTimeoutException
            || e instanceof ConnectException
            || (e instanceof HttpServerErrorException 
                && ((HttpServerErrorException) e).getStatusCode().is5xxServerError());
    }
}
```

**Retry Strategy:**
- **Max Retries**: 3 attempts
- **Backoff**: Exponential (1s, 2s, 4s)
- **Retryable Errors**: Network errors, timeouts, 5xx

#### **3. Async Retry with CompletableFuture**

```java
@Service
public class RepaymentServiceImpl {
    
    @Async("payoutThreadPoolExecutor")
    public CompletableFuture<Void> processRepaymentAsync(
            RepaymentRequest request) {
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    processRepayment(request);
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, payoutExecutor)
            .handle((result, exception) -> {
                if (exception != null) {
                    // Save for retry
                    saveForRetry(request, exception);
                }
                return result;
            });
    }
}
```

### **Circuit Breaker Pattern (Planned)**

```java
// Planned implementation with Resilience4j
@Service
public class LMSServiceImpl {
    
    @CircuitBreaker(name = "lms", fallbackMethod = "getLoanDetailsFallback")
    public GetLoanResponse getLoanDetails(String lmsLoanId) {
        return finfluxClient.getLoan(lmsLoanId);
    }
    
    public GetLoanResponse getLoanDetailsFallback(
            String lmsLoanId, Exception e) {
        log.error("Circuit breaker opened for LMS, using fallback", e);
        
        // Fallback: Return cached data or default response
        return getCachedLoanDetails(lmsLoanId)
            .orElseThrow(() -> new LMSException("LMS unavailable", e));
    }
}
```

**Circuit Breaker Configuration:**
```yaml
resilience4j.circuitbreaker:
  instances:
    lms:
      slidingWindowSize: 10
      minimumNumberOfCalls: 5
      permittedNumberOfCallsInHalfOpenState: 3
      automaticTransitionFromOpenToHalfOpenEnabled: true
      waitDurationInOpenState: 60s
      failureRateThreshold: 50
      slowCallRateThreshold: 100
      slowCallDurationThreshold: 2s
```

**States:**
- **CLOSED**: Normal operation
- **OPEN**: Circuit open, failing fast
- **HALF_OPEN**: Testing if service recovered

### **Timeout Handling**

```java
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        
        factory.setConnectTimeout(50000); // 50 seconds
        factory.setReadTimeout(50000); // 50 seconds
        factory.setConnectionRequestTimeout(50000); // 50 seconds
        
        return new RestTemplate(factory);
    }
}
```

**Timeout Strategy:**
- **Connection Timeout**: 50s
- **Read Timeout**: 50s
- **Request Timeout**: 50s
- **LMS Specific**: 140s (for long-running operations)

### **Graceful Degradation**

```java
@Service
public class RepaymentServiceImpl {
    
    public RepaymentResponse processRepayment(RepaymentRequest request) {
        try {
            // Primary flow: Synchronous processing
            return processRepaymentSync(request);
        } catch (Exception e) {
            if (isRetryableError(e)) {
                // Fallback: Async processing with retry
                processRepaymentAsync(request);
                return RepaymentResponse.builder()
                    .status("ACCEPTED")
                    .message("Processing asynchronously")
                    .build();
            }
            throw e;
        }
    }
}
```

---

## 🏛️ Current Architecture Deep Dive

### **Service Architecture**

```
┌────────────────────────────────────────────────────────────┐
│  LOAN REPAYMENT SERVICE ARCHITECTURE                       │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────┐  │
│  │              Controller Layer                       │  │
│  │  LoanController │ PaymentController │ WebhookCtrl   │  │
│  │  AdminController │ CronController │ ReportCtrl    │  │
│  └────────────────────────────────────────────────────┘  │
│                           │                                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │              Service Layer                          │  │
│  │  RepaymentService │ LMSService │ ENACHService      │  │
│  │  SettlementService │ PayoutService │ CronService   │  │
│  │  SplitPaymentAnalyzer │ ReportService              │  │
│  └────────────────────────────────────────────────────┘  │
│                           │                                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │           Repository Layer                          │  │
│  │  LoanRepository │ PaymentRepository │ WebhookRepo  │  │
│  │  SettlementRepository │ ScheduleRepository         │  │
│  └────────────────────────────────────────────────────┘  │
│                           │                                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │        Database Layer (Read-Write Separation)       │  │
│  │                                                      │  │
│  │  ┌──────────────┐       ┌──────────────┐          │  │
│  │  │  MASTER DB   │──────▶│   SLAVE DB   │          │  │
│  │  │  (Writes)    │ Repl  │   (Reads)    │          │  │
│  │  └──────────────┘       └──────────────┘          │  │
│  │         ▲                       ▲                    │  │
│  │         │                       │                    │  │
│  │   TransactionRoutingDataSource                     │  │
│  │   (AOP-based dynamic routing)                       │  │
│  └────────────────────────────────────────────────────┘  │
│                           │                                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │        Async Thread Pools                           │  │
│  │  payoutThreadPoolExecutor (20-50 threads)           │  │
│  │  lmsWebhookExecutor (20-50 threads)                │  │
│  │  slaveDbExecutor (read queries)                     │  │
│  │  masterDbExecutor (write queries)                   │  │
│  │  gpayFetchDemandExecutor (30-50 threads)           │  │
│  └────────────────────────────────────────────────────┘  │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### **External Integrations**

```
┌────────────────────────────────────────────────────────────┐
│  EXTERNAL INTEGRATIONS                                      │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │   Finflux    │  │   PayU       │  │    ENACH     │   │
│  │    (LMS)     │  │   Payout     │  │   Service    │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
│         │                 │                 │             │
│         └─────────────────┼─────────────────┘            │
│                           │                                │
│                  Loan Repayment Service                    │
│                                                              │
│  Integration Patterns:                                     │
│  ├── Synchronous: LMS loan creation, Payout VA creation    │
│  ├── Asynchronous: Webhook processing, Settlement          │
│  └── Batch: Daily repayment processing, Reports           │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### **Data Flow**

#### **Repayment Processing Flow**

```
1. VA Deposit Webhook Received
   ↓
2. Validate Webhook (HMAC, duplicate check)
   ↓
3. Save Webhook (status: IN_PROGRESS)
   ↓
4. Async Processing:
   ├── Fetch VA Balance
   ├── Fetch Loan Demand from LMS
   ├── Split Payment Analysis
   ├── Create Loan Payments
   ├── Create Merchant Settlements
   └── Post to LMS
   ↓
5. Update Webhook (status: SUCCESS/FAILED)
   ↓
6. Retry if Failed (cron-based)
```

#### **Settlement Flow**

```
1. Daily Settlement Cron (8:30 AM)
   ↓
2. Fetch Pending Settlements
   ↓
3. For each Settlement:
   ├── Validate Amount
   ├── Create Payout Request
   ├── Process Payout
   └── Update Settlement Status
   ↓
4. Generate Settlement Report
   ↓
5. Send Report via Email/SFTP
```

### **Database Schema**

#### **Core Tables**

```sql
-- Loan table
CREATE TABLE loan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(255) NOT NULL,
    lms_loan_id VARCHAR(255),
    status VARCHAR(50),
    loan_amount DECIMAL(15,2),
    disbursal_date DATE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    INDEX idx_application_id (application_id),
    INDEX idx_lms_loan_id (lms_loan_id)
);

-- Loan Payment table
CREATE TABLE loan_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_id BIGINT,
    amount DECIMAL(15,2),
    status VARCHAR(50),
    payment_mode VARCHAR(50),
    external_reference VARCHAR(255),
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP,
    INDEX idx_loan_id (loan_id),
    INDEX idx_idempotency_key (idempotency_key)
);

-- Merchant Settlement table
CREATE TABLE merchant_settlement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(255),
    amount DECIMAL(15,2),
    status VARCHAR(50),
    settlement_date DATE,
    created_at TIMESTAMP,
    INDEX idx_application_id (application_id)
);

-- Webhook table
CREATE TABLE webhook (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    external_id VARCHAR(255),
    type VARCHAR(50),
    status VARCHAR(50),
    retry_count INT DEFAULT 0,
    should_retry BOOLEAN DEFAULT true,
    payload TEXT,
    created_at TIMESTAMP,
    INDEX idx_external_id (external_id),
    INDEX idx_status_retry (status, should_retry)
);
```

---

## 💬 Behavioral Questions

### **1. Tell me about a challenging production issue you resolved**

**STAR Format:**

**Situation**: 
> "We had a production issue where repayment processing was failing for 20% of transactions during peak hours. The error logs showed 'Database connection pool exhausted' errors."

**Task**: 
> "I needed to identify the root cause and fix it without impacting ongoing transactions. The issue was affecting merchant settlements and loan repayments."

**Action**: 
> "I investigated the issue systematically:
> 1. Checked database connection pool configuration - found max pool size was 20
> 2. Analyzed query patterns - discovered long-running queries blocking connections
> 3. Checked thread pool configuration - found async threads were holding DB connections
> 4. Implemented read-write database separation to offload read queries
> 5. Increased connection pool size and added connection timeout
> 6. Optimized slow queries and added indexes
> 
> The fix involved:
> - Implementing TransactionRoutingDataSource for read-write separation
> - Increasing master pool to 20 and slave pool to 15
> - Adding @DataSource annotation for routing
> - Optimizing queries and adding proper indexes"

**Result**: 
> "The issue was resolved within 4 hours. Database connection pool exhaustion reduced from 20% to <0.1%. Query performance improved by 10x. System could handle 3x more concurrent requests. We also added monitoring alerts to prevent future occurrences."

### **2. How do you handle conflicting priorities?**

**Example:**

> "During a critical production issue, I had to balance:
> - Fixing the immediate issue (P0)
> - Delivering a feature for a partner launch (P1)
> - Code review for another team member (P2)
> 
> **Approach:**
> 1. **Immediate**: Focused on production issue first (P0)
> 2. **Communication**: Informed stakeholders about delay in feature delivery
> 3. **Delegation**: Asked tech lead to handle code review
> 4. **Time Management**: Worked on feature after resolving production issue
> 
> **Outcome**: Production issue resolved in 2 hours, feature delivered with 1-day delay, code review handled by tech lead. All stakeholders were informed and understood the prioritization."

### **3. Describe a time you improved system performance**

**Example:**

> "**Challenge**: API response times were high (5-10 seconds) due to synchronous processing of repayments.
> 
> **Analysis**: 
> - Repayment processing involved: VA balance fetch, LMS API call, settlement creation
> - All operations were synchronous, blocking the API thread
> - External API calls (LMS, Payout) were taking 2-5 seconds each
> 
> **Solution**:
> - Implemented async processing with CompletableFuture
> - Created dedicated thread pools for different operations
> - Moved heavy operations to background processing
> - API now returns immediately with 'ACCEPTED' status
> 
> **Results**:
> - API response time: 5-10s → 200-500ms (20x improvement)
> - Throughput: 100 TPS → 500+ TPS (5x improvement)
> - User experience: Immediate response instead of waiting
> - System can handle 5x more concurrent requests"

### **4. How do you ensure code quality?**

**Example:**

> "**Code Review Process**:
> 1. **Self-Review**: Review my own code before creating PR
> 2. **Unit Tests**: Write tests covering happy path, edge cases, error scenarios
> 3. **Integration Tests**: Test with real database and external services (mocked)
> 4. **Code Review**: Get at least 2 approvals before merging
> 5. **Static Analysis**: SonarQube checks for code smells, vulnerabilities
> 
> **Best Practices**:
> - Follow SOLID principles
> - Write self-documenting code
> - Add logging at appropriate levels
> - Handle exceptions properly
> - Use design patterns where appropriate
> 
> **Example**: When implementing split payment engine, I:
> - Wrote unit tests for all edge cases (insufficient balance, multiple loans, partial payments)
> - Added integration tests with real database
> - Got code review from 2 senior developers
> - Documented the algorithm and business logic
> - Added monitoring metrics for split payment operations"

### **5. Tell me about a time you had to learn something new quickly**

**Example:**

> "**Situation**: We needed to implement read-write database separation, but I had no prior experience with Spring's AbstractRoutingDataSource.
> 
> **Learning Process**:
> 1. **Research**: Read Spring documentation, blog posts, examples
> 2. **Prototype**: Created a small POC to understand the concept
> 3. **Implementation**: Implemented in staging environment first
> 4. **Testing**: Thoroughly tested with read and write operations
> 5. **Production**: Gradual rollout with monitoring
> 
> **Challenges**:
> - Understanding ThreadLocal for context propagation
> - AOP-based routing logic
> - Transaction management across data sources
> 
> **Outcome**: Successfully implemented read-write separation, improved query performance by 10x, shared knowledge with team through documentation and knowledge sharing session."

### **6. How do you handle production incidents?**

**Example:**

> "**Incident Response Process**:
> 
> **1. Immediate Response (0-15 min)**:
> - Acknowledge the incident
> - Assess severity and impact
> - Check monitoring dashboards
> - Check recent deployments
> 
> **2. Investigation (15-60 min)**:
> - Check application logs
> - Check database for data inconsistencies
> - Check external service health
> - Identify root cause
> 
> **3. Resolution (60 min - 4 hours)**:
> - Implement fix (hotfix if critical)
> - Test fix in staging
> - Deploy to production
> - Verify resolution
> 
> **4. Post-Incident (After resolution)**:
> - Document incident in post-mortem
> - Identify prevention measures
> - Update runbooks
> - Share learnings with team
> 
> **Example**: During a repayment processing failure:
> - **Detected**: Alert triggered for high error rate
> - **Investigated**: Found LMS API was timing out
> - **Resolved**: Implemented retry mechanism with exponential backoff
> - **Prevented**: Added circuit breaker pattern, improved timeout handling"

### **7. Describe a time you had to work with a difficult team member**

**Example:**

> "**Situation**: A team member was resistant to code review feedback, often pushing back on suggestions.
> 
> **Approach**:
> 1. **Understanding**: Had a one-on-one to understand their perspective
> 2. **Collaboration**: Instead of dictating, discussed pros/cons together
> 3. **Documentation**: Created team coding standards document
> 4. **Examples**: Showed real examples of how the suggested approach helped
> 5. **Patience**: Gave time to adapt, provided support
> 
> **Outcome**: Team member became more open to feedback, code quality improved, team collaboration strengthened."

### **8. How do you stay updated with technology?**

**Example:**

> "**Continuous Learning**:
> 1. **Blogs**: Follow tech blogs (Baeldung, DZone, InfoQ)
> 2. **Conferences**: Attend local meetups and conferences
> 3. **Online Courses**: Take courses on new technologies
> 4. **Side Projects**: Build personal projects to learn new tech
> 5. **Code Reviews**: Learn from team members' code
> 6. **Documentation**: Read official documentation for deep understanding
> 
> **Example**: When Spring Boot 3 was released:
> - Read release notes and migration guide
> - Migrated a personal project to Spring Boot 3
> - Proposed migration plan for loan-repayment service
> - Led the migration effort, shared learnings with team"

---

## 🎯 Key Talking Points Summary

### **Architecture Decisions**

1. **Microservices vs Monolith**
   - Loan Repayment: Microservices for independent scaling, fault isolation
   - ZipCredit: Monolith for transaction consistency, legacy evolution
   - Trade-offs understood and managed

2. **Read-Write Database Separation**
   - 10x performance improvement
   - Dynamic routing with AOP
   - Transparent to business logic

3. **Async Processing**
   - 20x faster API responses
   - Dedicated thread pools
   - Non-blocking operations

### **Performance Optimizations**

1. **Database**: Read-write separation, connection pooling, query optimization
2. **Async**: Thread pools, CompletableFuture, background processing
3. **Caching**: Redis for frequently accessed data
4. **Monitoring**: Prometheus, Grafana, ELK stack

### **Reliability**

1. **Idempotency**: Database unique constraints, idempotency keys
2. **Retry**: Exponential backoff, database-backed retries
3. **Circuit Breaker**: Planned implementation
4. **Timeout**: Proper timeout handling for external calls

### **Observability**

1. **Logging**: Structured logging with correlation IDs
2. **Metrics**: Micrometer, Prometheus
3. **Tracing**: Distributed tracing with W3C trace context
4. **Alerting**: Prometheus alerts, PagerDuty integration

### **Production Support**

1. **Debugging**: Systematic 5-phase approach
2. **Monitoring**: Real-time dashboards, alerts
3. **Incident Response**: Clear escalation path, post-mortem process
4. **Documentation**: Runbooks, architecture docs, API docs

---

## 📚 Additional Resources

### **Key Numbers to Remember**

- **10x**: Query performance improvement (read-write separation)
- **20x**: API response time improvement (async processing)
- **5x**: Throughput improvement (thread pools)
- **500-1000**: Webhooks/hour during peak hours
- **10,000-15,000**: Repayments/month
- **5+**: Dedicated thread pools
- **3**: Max retries for LMS API
- **5**: Max retries for webhooks
- **50s**: Connection/read timeout
- **140s**: LMS-specific timeout

### **Interview Preparation Checklist**

- [ ] Understand microservices vs monolith trade-offs
- [ ] Know load characteristics and performance metrics
- [ ] Explain reliability mechanisms (idempotency, retry, circuit breaker)
- [ ] Describe observability stack (logging, metrics, tracing)
- [ ] Walk through production debugging process
- [ ] Explain failure recovery mechanisms
- [ ] Describe current architecture
- [ ] Prepare behavioral question answers (STAR format)
- [ ] Know key numbers and metrics
- [ ] Practice explaining technical concepts simply

---

**Good luck with your interview! 🚀**

This document covers all aspects of Loan Repayment Service, Orchestration, and ZipCredit. Use it as your comprehensive preparation guide for the Hiring Manager round at Tide.
