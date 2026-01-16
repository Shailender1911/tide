# TIDE INTERVIEW PREP - PART 4: SYSTEM DESIGN SCENARIOS & OPERATIONAL EXCELLENCE
**Based on YOUR Actual PayU Lending Codebase**

---

## 6. SYSTEM DESIGN SCENARIOS

### Q: **CRITICAL SCENARIO** - A pod is processing millions of records (batch job), and it crashes mid-way. How do you ensure:
1. **Already processed records are not reprocessed**
2. **In-progress records are handled correctly**
3. **Unprocessed records are picked up by the new pod**
4. **Idempotency is maintained**

**A: Checkpoint-Based Recovery with Database State Tracking**

This is EXACTLY what we implemented in our CSV export service!

#### **Actual Implementation from Our Codebase:**

**1. Processing Strategy Selection:**
```java
// From: lending-project/loan-repayment/src/main/java/com/payu/vista/loanrepayment/reporting/strategy/ProcessingStrategy/ChunkedListProcessingStrategy.java

@Component
public class ChunkedListProcessingStrategy implements CSVProcessingStrategy {
    
    private static final int PROCESSING_CHUNK_SIZE = 1000;  // Process 1000 records at a time
    private static final int CSV_WRITE_BATCH_SIZE = 500;    // Write 500 rows at a time
    
    @Override
    public <T> CSVProcessingResult process(
            DataSupplier<T> dataSupplier,
            CSVSchema<T> schema,
            CSVWriter writer) {
        
        // Get all records to process
        List<T> dataList = dataSupplier.asAList();
        int totalRecords = dataList.size();
        
        log.info("Starting chunked processing for {} records", totalRecords);
        
        // Check if there's an existing checkpoint (for crash recovery)
        String jobId = generateJobId();
        ProcessingCheckpoint checkpoint = checkpointRepository.findByJobId(jobId);
        
        int startIdx = (checkpoint != null) ? checkpoint.getLastProcessedIndex() + 1 : 0;
        
        // Process in chunks
        for (int idx = startIdx; idx < totalRecords; idx += PROCESSING_CHUNK_SIZE) {
            int endIdx = Math.min(idx + PROCESSING_CHUNK_SIZE, totalRecords);
            
            // Process chunk
            ChunkResult result = processChunk(dataList, idx, endIdx, schema, writer);
            
            // CRITICAL: Save checkpoint after each chunk (enables crash recovery)
            saveCheckpoint(jobId, endIdx - 1, result);
            
            log.info("Processed chunk: {}-{} of {}", idx, endIdx, totalRecords);
        }
        
        // Mark job as completed
        markJobCompleted(jobId);
        
        return CSVProcessingResult.success(totalRecords);
    }
    
    private ChunkResult processChunk(List<T> dataList, int startIdx, int endIdx, 
                                     CSVSchema<T> schema, CSVWriter writer) {
        List<T> chunk = dataList.subList(startIdx, endIdx);
        
        // Process each record in chunk
        for (T record : chunk) {
            try {
                // Check if already processed (idempotency)
                if (isAlreadyProcessed(record)) {
                    log.warn("Skipping already processed record: {}", getRecordId(record));
                    continue;
                }
                
                // Process record
                String csvRow = schema.toCsvRow(record);
                writer.writeRow(csvRow);
                
                // Mark as processed
                markAsProcessed(record);
                
            } catch (Exception e) {
                log.error("Error processing record: {}", getRecordId(record), e);
                // Save to error table for manual review
                saveToErrorQueue(record, e.getMessage());
            }
        }
        
        return ChunkResult.success(chunk.size());
    }
    
    private void saveCheckpoint(String jobId, int lastProcessedIndex, ChunkResult result) {
        ProcessingCheckpoint checkpoint = checkpointRepository.findByJobId(jobId);
        
        if (checkpoint == null) {
            checkpoint = ProcessingCheckpoint.builder()
                .jobId(jobId)
                .build();
        }
        
        checkpoint.setLastProcessedIndex(lastProcessedIndex);
        checkpoint.setProcessedCount(checkpoint.getProcessedCount() + result.getSuccessCount());
        checkpoint.setErrorCount(checkpoint.getErrorCount() + result.getErrorCount());
        checkpoint.setLastUpdated(LocalDateTime.now());
        checkpoint.setStatus(ProcessingStatus.IN_PROGRESS);
        
        checkpointRepository.save(checkpoint);
    }
}
```

**2. Database Schema for Checkpoint Tracking:**
```sql
-- Checkpoint table for crash recovery
CREATE TABLE processing_checkpoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id VARCHAR(255) UNIQUE NOT NULL,
    job_type VARCHAR(50) NOT NULL,  -- CSV_EXPORT, DISBURSAL_BATCH, etc.
    total_records INT,
    last_processed_index INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    status ENUM('IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_job_id (job_id),
    INDEX idx_status (status)
);

-- Processed records tracking (for idempotency)
CREATE TABLE processed_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id VARCHAR(255) NOT NULL,
    record_id VARCHAR(255) NOT NULL,  -- application_id, loan_id, etc.
    record_type VARCHAR(50),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_record (job_id, record_id),
    INDEX idx_job_id (job_id),
    INDEX idx_record_id (record_id)
);

-- Error queue for failed records
CREATE TABLE processing_errors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id VARCHAR(255) NOT NULL,
    record_id VARCHAR(255),
    record_data TEXT,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    status ENUM('PENDING', 'RETRYING', 'RESOLVED', 'MANUAL_REVIEW'),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP NULL,
    INDEX idx_job_id (job_id),
    INDEX idx_status (status)
);
```

**3. Crash Recovery Implementation:**
```java
@Service
public class BatchJobRecoveryService {
    
    @Scheduled(cron = "0 */5 * * * ?")  // Every 5 minutes
    public void recoverStuckJobs() {
        // Find jobs stuck in IN_PROGRESS for >10 minutes
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        
        List<ProcessingCheckpoint> stuckJobs = checkpointRepository.findByStatusAndLastUpdatedBefore(
            ProcessingStatus.IN_PROGRESS,
            threshold
        );
        
        for (ProcessingCheckpoint checkpoint : stuckJobs) {
            log.warn("Detected stuck job: {} - last updated: {}", 
                checkpoint.getJobId(), checkpoint.getLastUpdated());
            
            // Resume job from last checkpoint
            resumeJob(checkpoint);
        }
    }
    
    private void resumeJob(ProcessingCheckpoint checkpoint) {
        String jobId = checkpoint.getJobId();
        
        log.info("Resuming job: {} from index: {}", jobId, checkpoint.getLastProcessedIndex());
        
        // Get job configuration
        JobConfig config = jobConfigRepository.findByJobId(jobId);
        
        // Re-trigger processing from last checkpoint
        CompletableFuture.runAsync(() -> {
            try {
                CSVProcessingStrategy strategy = new ChunkedListProcessingStrategy();
                
                // Resume from last checkpoint
                strategy.process(
                    config.getDataSupplier(),
                    config.getSchema(),
                    config.getWriter()
                    // The strategy will automatically resume from checkpoint
                );
                
            } catch (Exception e) {
                log.error("Job recovery failed: {}", jobId, e);
                markJobFailed(jobId, e.getMessage());
            }
        }, taskExecutor);
    }
}
```

**4. Idempotency Check:**
```java
private boolean isAlreadyProcessed(Record record) {
    String recordId = getRecordId(record);
    String jobId = getCurrentJobId();
    
    // Check in database
    boolean exists = processedRecordsRepository.existsByJobIdAndRecordId(jobId, recordId);
    
    if (exists) {
        log.debug("Record already processed: {} in job: {}", recordId, jobId);
        return true;
    }
    
    return false;
}

private void markAsProcessed(Record record) {
    String recordId = getRecordId(record);
    String jobId = getCurrentJobId();
    
    // Insert into processed_records table
    ProcessedRecord pr = ProcessedRecord.builder()
        .jobId(jobId)
        .recordId(recordId)
        .recordType(record.getClass().getSimpleName())
        .processedAt(LocalDateTime.now())
        .build();
    
    try {
        processedRecordsRepository.save(pr);
    } catch (DataIntegrityViolationException e) {
        // Duplicate key - already processed (race condition)
        log.warn("Record already marked as processed: {}", recordId);
    }
}
```

**5. K8s Pod Disruption Budget (Prevents Multiple Crashes):**
```yaml
# Ensure at least 1 pod is always running
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: batch-processor-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: batch-processor
```

### **Complete Flow:**

```
Initial State:
- Job starts, 1M records to process
- Checkpoint: { lastProcessedIndex: 0, status: IN_PROGRESS }

Processing:
- Process chunk 0-1000 → Save checkpoint (1000)
- Process chunk 1000-2000 → Save checkpoint (2000)
- Process chunk 2000-3000 → Save checkpoint (3000)
- ... continues ...
- Process chunk 500000-501000 → Save checkpoint (501000)
- ** POD CRASHES ** (at index 501000)

New Pod Starts:
1. Recovery service detects stuck job (no update in 10 mins)
2. Reads checkpoint: { lastProcessedIndex: 501000 }
3. Resumes processing from index 501001
4. Checks each record with isAlreadyProcessed()
   - Records 0-501000: Already processed (skip)
   - Records 501001-1000000: Process normally
5. Checkpoint updates continue: 502000, 503000, ...
6. Job completes: { lastProcessedIndex: 1000000, status: COMPLETED }
```

### **Cross-Question 1: What if the database is also down during the crash?**

**A:** **Write-Ahead Log (WAL) + File-Based Checkpoint**

```java
@Service
public class FileBasedCheckpointService {
    
    private static final String CHECKPOINT_DIR = "/tmp/checkpoints";
    
    public void saveCheckpoint(String jobId, int lastProcessedIndex) {
        // Write to local file first (faster, survives DB downtime)
        File checkpointFile = new File(CHECKPOINT_DIR + "/" + jobId + ".checkpoint");
        
        try (FileWriter writer = new FileWriter(checkpointFile)) {
            CheckpointData data = CheckpointData.builder()
                .jobId(jobId)
                .lastProcessedIndex(lastProcessedIndex)
                .timestamp(LocalDateTime.now())
                .build();
            
            writer.write(objectMapper.writeValueAsString(data));
            writer.flush();
            
            // Then sync to database (asynchronously)
            CompletableFuture.runAsync(() -> syncToDatabase(data));
            
        } catch (IOException e) {
            log.error("Failed to write checkpoint file", e);
        }
    }
    
    public ProcessingCheckpoint loadCheckpoint(String jobId) {
        // Try database first
        ProcessingCheckpoint dbCheckpoint = checkpointRepository.findByJobId(jobId);
        
        if (dbCheckpoint != null) {
            return dbCheckpoint;
        }
        
        // Fallback to file if database is down
        File checkpointFile = new File(CHECKPOINT_DIR + "/" + jobId + ".checkpoint");
        
        if (checkpointFile.exists()) {
            try {
                String content = Files.readString(checkpointFile.toPath());
                CheckpointData data = objectMapper.readValue(content, CheckpointData.class);
                
                return ProcessingCheckpoint.from(data);
                
            } catch (IOException e) {
                log.error("Failed to read checkpoint file", e);
            }
        }
        
        return null;
    }
}
```

### **Cross-Question 2: How do you handle records that are "in progress" when pod crashes?**

**A:** **Lease-Based Locking**

```java
@Service
public class LeaseBasedProcessingService {
    
    private static final int LEASE_DURATION_SECONDS = 60;
    
    public void processWithLease(Record record) {
        String recordId = getRecordId(record);
        
        // Try to acquire lease
        RecordLease lease = acquireLease(recordId);
        
        if (lease == null) {
            log.warn("Failed to acquire lease for record: {}", recordId);
            return;  // Another pod is processing this record
        }
        
        try {
            // Process record
            processRecord(record);
            
            // Mark as completed (releases lease)
            markAsProcessed(recordId);
            releaseLease(lease);
            
        } catch (Exception e) {
            log.error("Error processing record: {}", recordId, e);
            // Lease will expire after 60 seconds, allowing retry
        }
    }
    
    private RecordLease acquireLease(String recordId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(LEASE_DURATION_SECONDS);
        
        try {
            RecordLease lease = RecordLease.builder()
                .recordId(recordId)
                .podId(getPodId())
                .acquiredAt(now)
                .expiresAt(expiresAt)
                .status(LeaseStatus.ACTIVE)
                .build();
            
            return recordLeaseRepository.save(lease);
            
        } catch (DataIntegrityViolationException e) {
            // Lease already exists - check if expired
            RecordLease existingLease = recordLeaseRepository.findByRecordId(recordId);
            
            if (existingLease.getExpiresAt().isBefore(now)) {
                // Lease expired - take over
                existingLease.setPodId(getPodId());
                existingLease.setAcquiredAt(now);
                existingLease.setExpiresAt(expiresAt);
                
                return recordLeaseRepository.save(existingLease);
            }
            
            return null;  // Active lease held by another pod
        }
    }
}
```

**Database Schema:**
```sql
CREATE TABLE record_lease (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id VARCHAR(255) UNIQUE NOT NULL,
    pod_id VARCHAR(255) NOT NULL,
    acquired_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status ENUM('ACTIVE', 'RELEASED', 'EXPIRED'),
    INDEX idx_expires_at (expires_at),
    INDEX idx_pod_id (pod_id)
);
```

---

## 7. CODE REVIEW BEST PRACTICES

### Q: What do you look for during code reviews? What's your checklist?

**A: Comprehensive Code Review Checklist**

Based on my **1,900+ commits** in lending systems, here's what I check:

#### **1. Architecture & Design (SOLID Principles)**

```java
// ❌ BAD: Violation of Single Responsibility Principle
public class LoanService {
    public void createLoan(LoanRequest request) {
        // Validate request
        if (request.getAmount() < 5000) throw new ValidationException();
        
        // Create loan
        Loan loan = loanRepository.save(Loan.from(request));
        
        // Send email (should be separate service!)
        emailService.send(loan.getCustomerEmail(), "Loan created");
        
        // Update analytics (should be event-driven!)
        analyticsService.track("loan_created", loan.getId());
    }
}

// ✅ GOOD: Separation of concerns
public class LoanService {
    @Autowired private LoanValidationService validationService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    
    public Loan createLoan(LoanRequest request) {
        // Validate
        validationService.validate(request);
        
        // Create loan
        Loan loan = loanRepository.save(Loan.from(request));
        
        // Publish event (subscribers handle email, analytics)
        eventPublisher.publishEvent(new LoanCreatedEvent(loan));
        
        return loan;
    }
}
```

**Code Review Comment:**
```
❌ LoanService is doing too much. Extract email and analytics into event listeners.
✅ Suggestion: Use Spring Events pattern (ApplicationEventPublisher).
```

#### **2. Performance & Scalability**

```java
// ❌ BAD: N+1 Query Problem
public List<ApplicationDTO> getAllApplications() {
    List<Application> applications = applicationRepository.findAll();
    
    return applications.stream()
        .map(app -> {
            // Lazy loading triggers additional query for each application!
            Customer customer = app.getCustomer();
            return ApplicationDTO.from(app, customer);
        })
        .collect(Collectors.toList());
}

// ✅ GOOD: Fetch with JOIN
@Query("SELECT a FROM Application a JOIN FETCH a.customer")
List<Application> findAllWithCustomer();

public List<ApplicationDTO> getAllApplications() {
    // Single query with JOIN
    List<Application> applications = applicationRepository.findAllWithCustomer();
    return applications.stream()
        .map(app -> ApplicationDTO.from(app, app.getCustomer()))
        .collect(Collectors.toList());
}
```

**Code Review Comment:**
```
❌ N+1 query detected. This will execute 1 + N queries for N applications.
✅ Use @Query with JOIN FETCH or @EntityGraph.
Performance impact: 1000 applications = 1001 queries → 1 query
```

#### **3. Security**

```java
// ❌ BAD: SQL Injection vulnerability
public List<Application> findByCustomerName(String name) {
    String sql = "SELECT * FROM application WHERE customer_name = '" + name + "'";
    return jdbcTemplate.query(sql, applicationRowMapper);
}

// ✅ GOOD: Parameterized query
public List<Application> findByCustomerName(String name) {
    String sql = "SELECT * FROM application WHERE customer_name = ?";
    return jdbcTemplate.query(sql, applicationRowMapper, name);
}

// ❌ BAD: Sensitive data in logs
log.info("Processing application: {}", application);  // Logs PAN, Aadhaar!

// ✅ GOOD: Mask sensitive data
log.info("Processing application: {} for customer: {}", 
    application.getApplicationId(),
    maskCustomerDetails(application.getCustomerId())
);
```

**Code Review Comment:**
```
❌ CRITICAL: SQL injection vulnerability. Never concatenate user input into SQL.
❌ Sensitive data leakage in logs. PAN and Aadhaar should never be logged.
✅ Use parameterized queries and mask sensitive fields.
```

#### **4. Error Handling & Reliability**

```java
// ❌ BAD: Swallowing exceptions
public void processPayment(Payment payment) {
    try {
        nachService.initiatePayment(payment);
    } catch (Exception e) {
        // Silent failure! Payment lost!
    }
}

// ✅ GOOD: Proper error handling
public void processPayment(Payment payment) {
    try {
        nachService.initiatePayment(payment);
        
    } catch (NACHUnavailableException e) {
        // Transient error - retry later
        log.warn("NACH service unavailable, queueing for retry", e);
        paymentRetryQueue.add(payment);
        
    } catch (InvalidMandateException e) {
        // Permanent error - notify user
        log.error("Invalid NACH mandate for payment: {}", payment.getId(), e);
        notificationService.notifyCustomer(payment.getCustomerId(), "Payment failed - invalid mandate");
        markPaymentFailed(payment.getId(), e.getMessage());
        
    } catch (Exception e) {
        // Unexpected error - alert operations
        log.error("Unexpected error processing payment: {}", payment.getId(), e);
        alertService.sendAlert("Payment processing error", e);
        throw e;  // Let it bubble up
    }
}
```

**Code Review Comment:**
```
❌ Empty catch block swallows exceptions. Payment will be lost silently.
✅ Handle errors appropriately:
   - Transient errors → Retry
   - Permanent errors → Log + Notify
   - Unexpected errors → Alert operations
```

#### **5. Testing**

```java
// ❌ BAD: Untestable code (tight coupling)
public class LoanServiceImpl {
    public Loan createLoan(LoanRequest request) {
        // Hardcoded dependency - can't mock for testing!
        LoanRepository repository = new LoanRepositoryImpl();
        return repository.save(Loan.from(request));
    }
}

// ✅ GOOD: Dependency injection (testable)
@Service
public class LoanServiceImpl {
    @Autowired
    private LoanRepository repository;
    
    public Loan createLoan(LoanRequest request) {
        return repository.save(Loan.from(request));
    }
}

// Unit test
@Test
public void testCreateLoan() {
    // Mock repository
    LoanRepository mockRepository = mock(LoanRepository.class);
    when(mockRepository.save(any())).thenReturn(mockLoan);
    
    LoanServiceImpl service = new LoanServiceImpl(mockRepository);
    Loan result = service.createLoan(request);
    
    assertEquals(mockLoan.getId(), result.getId());
    verify(mockRepository).save(any());
}
```

**Code Review Comment:**
```
❌ No unit tests provided. Code is not testable (tight coupling).
✅ Add:
   1. Unit tests for business logic
   2. Integration tests for repository layer
   3. Contract tests for API endpoints
Target: >80% coverage
```

#### **6. Configuration Management**

```java
// ❌ BAD: Hardcoded values
public class ZipCreditClient {
    private static final String BASE_URL = "https://prod-zipcredit.payufin.io";
    private static final int TIMEOUT = 50000;
}

// ✅ GOOD: Externalized configuration
@Component
@ConfigurationProperties(prefix = "zipcredit")
public class ZipCreditConfig {
    private String baseUrl;
    private int timeout;
    private boolean enableRetry;
    
    // Getters/setters
}

// application.properties
zipcredit.baseUrl=https://prod-zipcredit.payufin.io
zipcredit.timeout=50000
zipcredit.enableRetry=true
```

**Code Review Comment:**
```
❌ Hardcoded configuration. Can't change without rebuild.
✅ Externalize to application.properties or config service.
```

#### **7. Code Style & Readability**

```java
// ❌ BAD: Unclear variable names, no comments
public boolean v(Loan l) {
    if (l.getA() > 10000 && l.getS().equals("A") && l.getC().getCs() > 700) {
        return true;
    }
    return false;
}

// ✅ GOOD: Clear names, documented logic
/**
 * Validates if loan qualifies for auto-approval.
 * 
 * Auto-approval criteria:
 * - Loan amount > 10,000
 * - Loan status = APPROVED
 * - Customer credit score > 700
 */
public boolean qualifiesForAutoApproval(Loan loan) {
    return loan.getAmount().compareTo(new BigDecimal(10000)) > 0
        && loan.getStatus() == LoanStatus.APPROVED
        && loan.getCustomer().getCreditScore() > 700;
}
```

**My Code Review Template:**
```markdown
## Architecture & Design
- [ ] Follows SOLID principles
- [ ] Separation of concerns (Controller/Service/Repository)
- [ ] Design patterns used appropriately
- [ ] No cyclic dependencies

## Performance
- [ ] No N+1 query problems
- [ ] Database queries optimized (indexes used)
- [ ] Caching strategy appropriate
- [ ] Pagination implemented for large datasets

## Security
- [ ] Input validation comprehensive
- [ ] No SQL injection vulnerabilities
- [ ] Sensitive data not logged
- [ ] Authentication/authorization checks present

## Reliability
- [ ] Error handling comprehensive
- [ ] Retry logic for transient failures
- [ ] Circuit breaker for external calls
- [ ] Timeouts configured appropriately

## Testing
- [ ] Unit tests present (>80% coverage)
- [ ] Integration tests for critical paths
- [ ] Test data not hardcoded
- [ ] Edge cases covered

## Code Quality
- [ ] Variable/method names clear
- [ ] Comments explain "why", not "what"
- [ ] No commented-out code
- [ ] Consistent formatting

## Configuration
- [ ] No hardcoded values
- [ ] Secrets not in code (use Vault/env vars)
- [ ] Feature flags for experimental features
```

---

## 8. MANAGING TIGHT DEADLINES

### Q: How do you manage tight deadlines? For example, critical production issue + feature delivery tomorrow?

**A: Priority Matrix + Time Boxing**

**Real Scenario from My Experience:**

```
Date: Jan 10, 2024
Critical Issue: GPay TL loans failing (production down)
Deadline: Meesho auto-disbursal feature (committed to PM for next day)
```

**My Approach:**

#### **Step 1: Assess & Prioritize (5 minutes)**

```
Priority Matrix:
┌─────────────────────────────────────┬──────────────────────────────────┐
│         URGENT & IMPORTANT           │      NOT URGENT & IMPORTANT      │
│                                     │                                 │
│  P0: GPay Production Issue          │  P2: Meesho Auto-Disbursal MVP  │
│  Impact: 100% failure rate          │  Impact: Business commitment    │
│  Action: Hotfix now                 │  Action: Core functionality only│
│                                     │                                 │
├─────────────────────────────────────┼──────────────────────────────────┤
│       URGENT & NOT IMPORTANT         │   NOT URGENT & NOT IMPORTANT    │
│                                     │                                 │
│  P3: Code review for teammate       │  P4: Refactoring old code       │
│  Action: Defer to tomorrow          │  Action: Add to backlog         │
│                                     │                                 │
└─────────────────────────────────────┴──────────────────────────────────┘
```

#### **Step 2: Communicate (Immediately)**

**Slack to Team:**
```
🚨 PRODUCTION ISSUE: GPay TL loans failing (100% failure rate)

IMMEDIATE ACTIONS:
- I'm investigating root cause (ETA: 30 mins)
- @ops-team: Monitor alerts, prepare rollback if needed
- @pm-team: FYI - May delay Meesho feature by 4 hours

UPDATES: Will update every 30 mins
```

**Email to PM:**
```
Subject: Production Issue - Impact on Meesho Delivery

Hi PM,

Critical production issue detected:
- Issue: GPay loans failing
- Impact: 100% failure rate
- Timeline: Fix by 2 PM (4 hours)

Meesho Auto-Disbursal:
- Original commitment: Tomorrow 9 AM
- Revised: Tomorrow 1 PM (4 hours delay)
- Scope: MVP only (advanced features in v2)

Risk Mitigation:
- Core functionality delivered by 1 PM
- Full testing by 3 PM
- Deployment by 5 PM

Let me know if this works.
```

#### **Step 3: Time-Box Tasks**

```
Time Allocation (8-hour workday):

9:00 AM - 12:00 PM (3 hours): Production Issue
├── 9:00 - 9:30: Investigation (logs, DB, code)
├── 9:30 - 10:30: Root cause analysis (cache race condition)
├── 10:30 - 11:00: Implement fix (bypass cache + retry)
├── 11:00 - 11:30: Testing (unit + integration)
└── 11:30 - 12:00: Deployment + monitoring

12:00 PM - 1:00 PM: Lunch + Buffer

1:00 PM - 6:00 PM (5 hours): Meesho Feature (MVP)
├── 1:00 - 2:00: Design (Factory pattern)
├── 2:00 - 4:00: Implementation (core logic only)
├── 4:00 - 5:00: Testing (happy path + edge cases)
└── 5:00 - 6:00: Code review + PR

6:00 PM - 7:00 PM: Buffer for issues
```

#### **Step 4: MVP Scoping**

**Original Scope:**
```
✅ Auto-disbursal for Meesho (MUST HAVE)
✅ Amount-based logic (MUST HAVE)
❌ Partner-specific configurations (DEFER)
❌ Audit dashboard (DEFER)
❌ Email notifications (DEFER)
```

**MVP Implementation:**
```java
// Just enough to work - can extend later
@Service
public class AutoDisbursalService {
    
    public boolean shouldAutoDisburse(Loan loan) {
        // Simple rule: Auto-disburse if amount < 50,000
        return loan.getPartner() == Partner.MEESHO
            && loan.getAmount().compareTo(new BigDecimal(50000)) < 0;
    }
}

// Full factory pattern (extensible for v2)
@Component
public class AutoDisbursalFactory {
    public AutoDisbursalHandler getHandler(Partner partner) {
        return handlerMap.getOrDefault(partner, defaultHandler);
    }
}
```

#### **Step 5: Parallel Work (Leverage Team)**

**Delegation:**
```
Me: Core auto-disbursal logic
Teammate 1: Unit tests (parallel)
Teammate 2: Integration tests (parallel)
Teammate 3: Code review (while I fix prod issue)
```

### **Result:**
- ✅ Production issue fixed: 11:45 AM (ahead of 12 PM target)
- ✅ Meesho MVP delivered: 5:30 PM (ahead of next-day target)
- ✅ No compromise on quality (80% test coverage, pattern-based design)

### **Cross-Question: What if you can't meet both deadlines?**

**A: Escalate Early with Options**

**Email to Leadership:**
```
Subject: Timeline Conflict - Need Decision

SITUATION:
- Critical prod issue: 3-4 hours to fix
- Meesho feature: Committed for tomorrow 9 AM

OPTIONS:
1. Delay Meesho by 4 hours (deliver by 1 PM) ✅ RECOMMENDED
   Risk: Low, scope reduced to MVP
   
2. Delegate prod issue to senior engineer
   Risk: Medium, they're unfamiliar with GPay integration
   
3. Work late (till 10 PM) to meet both
   Risk: High, fatigue may cause bugs

RECOMMENDATION: Option 1
- Production takes priority (customer impact)
- MVP scope acceptable for business needs
- Full features delivered in v1.1 next week

Need decision by 10 AM.
```

---

*Continue to Part 5 for Monolithic vs Microservices and Final Cross-Questions...*
