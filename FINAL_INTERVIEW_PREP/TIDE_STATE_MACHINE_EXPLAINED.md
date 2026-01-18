# 🎯 STATE MACHINE & TRIGGER SYSTEM - HOW IT ACTUALLY WORKS

**For Tide Managerial Interview - Clear Explanation with Cross-Questions**

---

## 📚 TABLE OF CONTENTS
1. [What is Your "State Machine"?](#1-what-is-your-state-machine)
2. [How Triggers Work](#2-how-triggers-work)
3. [Example: LMS_CLIENT_SETUP → CREATE_LOAN_TL Flow](#3-example-flow)
4. [Concurrency Control](#4-concurrency-control)
5. [Why This Design?](#5-why-this-design)
6. [Cross-Questions & Answers](#6-cross-questions--answers)

---

## 1. WHAT IS YOUR "STATE MACHINE"?

### **Core Concept:**
We DON'T have a traditional state machine (like FSM with enum states). Instead, we have a **Progress Tracking System** with:

### **Two Tables:**

**Table 1: `application_state` (Boolean Flags)**
```
Tracks WHAT has been completed (like checkboxes)

Example for APP123:
┌────────────────────────────────┬─────────┐
│ Flag                           │ Value   │
├────────────────────────────────┼─────────┤
│ is_application_id_created      │ ✅ true  │
│ is_eligible                    │ ✅ true  │
│ is_documents_uploaded          │ ✅ true  │
│ is_nach_registered             │ ✅ true  │
│ is_loan_created                │ ❌ false │
│ is_loan_disbursed              │ ❌ false │
└────────────────────────────────┴─────────┘
```

**Table 2: `a_application_stage_tracker` (Stage History)**
```
Tracks WHEN stages happened (audit trail + triggers)

Example for APP123:
┌────┬──────────────┬─────────────────────────────────┬────────────────────┐
│ ID │ application  │ current_status                  │ created_at         │
├────┼──────────────┼─────────────────────────────────┼────────────────────┤
│ 1  │ APP123       │ APPLICATION_CREATED             │ 2026-01-15 10:00   │
│ 2  │ APP123       │ PARTNER_APPROVED                │ 2026-01-15 10:05   │
│ 3  │ APP123       │ LMS_CLIENT_SETUP                │ 2026-01-15 10:10   │
│ 4  │ APP123       │ CREATE_LOAN_TL_SUCCESS          │ 2026-01-15 10:15   │ ← Triggers next event
│ 5  │ APP123       │ LOAN_DISBURSAL_SUCCESS          │ 2026-01-15 10:20   │
└────┴──────────────┴─────────────────────────────────┴────────────────────┘
```

### **Key Difference from Traditional State Machine:**

| Traditional State Machine | Your System |
|--------------------------|-------------|
| **One state at a time** (enum) | **Multiple flags** (boolean) |
| Current state: `APPROVED` | `is_eligible: true`, `is_documents_uploaded: false` |
| Transitions: `PENDING → APPROVED → REJECTED` | Flags only go: `false → true` (never reverse) |
| Can rollback: `APPROVED → PENDING` | ❌ Cannot rollback (flags monotonic) |
| State replaces previous | History preserved in tracker table |

---

## 2. HOW TRIGGERS WORK

### **The Trigger Flow (Step-by-Step):**

```
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Business Logic Completes                               │
│                                                                 │
│ Example: LMS Client Setup API succeeds                         │
│ Response: { client_id: "CL123", status: "SUCCESS" }            │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Insert Stage into Tracker                              │
│                                                                 │
│ applicationStatusServiceImpl.insertApplicationTracker(          │
│     applicationId = "APP123",                                   │
│     tenantId = 1,                                               │
│     currentStatus = ApplicationStage.LMS_CLIENT_SETUP           │
│ )                                                               │
│                                                                 │
│ This INSERTS a new row:                                         │
│ ┌────┬────────┬──────────────────────┬───────────────────────┐ │
│ │ ID │ app_id │ current_status       │ created_at            │ │
│ ├────┼────────┼──────────────────────┼───────────────────────┤ │
│ │ 3  │ APP123 │ LMS_CLIENT_SETUP     │ 2026-01-15 10:10:00   │ │
│ └────┴────────┴──────────────────────┴───────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Trigger Service Invoked (SAME TRANSACTION)             │
│                                                                 │
│ Inside insertApplicationTracker() method:                       │
│ 1. saveCurrentStatus(...)  ← Inserts row in DB                 │
│ 2. processTriggers(...)    ← Looks up events for this stage    │
│                                                                 │
│ processTriggers() calls:                                        │
│ triggerService.process(                                         │
│     prevStage = PARTNER_APPROVED,                               │
│     currentStage = LMS_CLIENT_SETUP,                            │
│     applicationId = "APP123",                                   │
│     tenantId = 1                                                │
│ )                                                               │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Look Up Configured Events                              │
│                                                                 │
│ TriggerServiceImpl has a CONFIG MAP (loaded from DB):          │
│                                                                 │
│ partnerStageEventConfigMap = {                                  │
│     "GPAY": {                                                   │
│         ApplicationStage.LMS_CLIENT_SETUP: [                    │
│             EventConfig {                                       │
│                 eventType: CREATE_LOAN_TL,                      │
│                 channelCode: "GPAY"                             │
│             }                                                   │
│         ]                                                       │
│     }                                                           │
│ }                                                               │
│                                                                 │
│ Lookup Result:                                                  │
│ Stage: LMS_CLIENT_SETUP → Event: CREATE_LOAN_TL               │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Execute Event ASYNCHRONOUSLY                            │
│                                                                 │
│ IEventService eventService =                                    │
│     eventServiceFactory.get(EventType.CREATE_LOAN_TL);          │
│                                                                 │
│ CompletableFuture.runAsync(() -> {                              │
│     eventService.process(applicationDetails);                   │
│ }, eventThreadPoolExecutor);                                    │
│                                                                 │
│ ↓ Runs in SEPARATE THREAD (non-blocking)                       │
│                                                                 │
│ CreateLoanTLEventServiceImpl.process() {                        │
│     1. Acquire distributed lock: "CREATE_LOAN_TL:APP123"        │
│     2. Check idempotency: Is loan already created?              │
│     3. Call LMS API: createLoan(APP123)                         │
│     4. Save loan_id in DB                                       │
│     5. Release lock                                             │
│     6. Insert tracker: CREATE_LOAN_TL_SUCCESS                   │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: SUCCESS Stage Triggers Next Event                      │
│                                                                 │
│ When CREATE_LOAN_TL_SUCCESS is inserted:                        │
│ → Triggers: LOAN_DISBURSAL event                               │
│                                                                 │
│ LoanDisbursalEventServiceImpl.process() {                       │
│     1. Acquire lock: "DISBURSE_LOAN:APP123"                     │
│     2. Call Payout API                                          │
│     3. Insert tracker: LOAN_DISBURSAL_SUCCESS                   │
│     4. Triggers: WEBHOOK_TO_PARTNER event                       │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

### **Critical Points:**

1. **NOT a database trigger** - It's Java code that runs AFTER inserting into tracker table (same method)
2. **NOT polling** - Events fire immediately when stage is inserted
3. **Asynchronous** - Events run in background thread pool (non-blocking)
4. **Event chaining** - Each success stage can trigger the next event

---

## 3. EXAMPLE FLOW: LMS_CLIENT_SETUP → CREATE_LOAN_TL

### **Real-World Scenario (GPay Application):**

```
User completes KYC + NACH → System creates LMS client
```

### **Code Flow:**

```java
// 1. Business Logic (ZipCredit Service)
public void setupLmsClient(String applicationId) {
    // Call Finflux API to create client
    Response response = lmsApiService.createClient(applicationId);
    
    if (response.isSuccess()) {
        // Save client_id in DB
        saveClientId(applicationId, response.getClientId());
        
        // 2. INSERT STAGE (this triggers events)
        applicationStatusServiceImpl.insertApplicationTracker(
            applicationId, 
            tenantId, 
            ApplicationStage.LMS_CLIENT_SETUP  // ← This is the trigger
        );
    }
}
```

**What Happens Inside `insertApplicationTracker()`:**

```java
// ApplicationStatusServiceImpl.java

public boolean insertApplicationTracker(
        String applicationId, 
        Integer tenantId,
        ApplicationStage currentStatus) {
    
    try {
        // Get application details (channel_code needed for trigger lookup)
        ApplicationBean application = getApplicationDetails(applicationId, tenantId);
        
        // Get previous stage (for context)
        String prevStatus = getPrevStatus(applicationId, tenantId);
        
        // SAVE current stage to DB
        saveCurrentStatus(applicationId, tenantId, currentStatus, prevStatus);
        
        // TRIGGER EVENTS (immediately after save)
        processTriggers(applicationId, tenantId, currentStatus, prevStatus);
        
        // Notify partner (async)
        processPartnerStatusAndNotification(application, currentStatus);
        
        return true;
    } catch (Exception e) {
        logger.error("Error in updating application tracker", e);
    }
    return false;
}
```

**Inside `processTriggers()`:**

```java
private void processTriggers(
        String applicationId, 
        Integer tenantId, 
        ApplicationStage currentStatus,
        String prevStatus) {
    
    try {
        ApplicationStage prevStage = ApplicationStage.valueOf(prevStatus);
        
        // Call TriggerService
        triggerService.process(prevStage, currentStatus, applicationId, tenantId);
        
    } catch (Exception e) {
        logger.error("Exception in executing trigger for stage {}", currentStatus, e);
    }
}
```

**Inside `TriggerService.process()`:**

```java
// TriggerServiceImpl.java

public void process(
        ApplicationStage from, 
        ApplicationStage to, 
        String applicationId, 
        Integer tenantId) {
    
    // Get application details (need channel_code)
    ApplicationBean application = applicationDBService.selectById(applicationId, tenantId);
    String channelCode = application.getChannel_code();
    
    // LOOKUP: What events are configured for this channel + stage?
    List<EventConfig> eventConfigs = partnerStageEventConfigMap
        .get(channelCode)
        .get(to);  // "to" = LMS_CLIENT_SETUP
    
    if (eventConfigs == null || eventConfigs.isEmpty()) {
        logger.info("No events configured for stage {}", to);
        return;
    }
    
    // EXECUTE each configured event
    for (EventConfig eventConfig : eventConfigs) {
        EventType eventType = eventConfig.getEventType();  // CREATE_LOAN_TL
        
        // Get event service implementation (Factory Pattern)
        IEventService eventService = eventServiceFactory.get(eventType);
        
        // Create DTO
        ApplicationDetailsDTO dto = ApplicationDetailsDTO.builder()
            .applicationId(applicationId)
            .tenantId(tenantId)
            .channelCode(channelCode)
            .build();
        
        // EXECUTE ASYNCHRONOUSLY (non-blocking)
        CompletableFuture.runAsync(() -> {
            try {
                eventService.process(dto);  // ← CREATE_LOAN_TL event runs here
            } catch (Exception e) {
                logger.error("Event processing failed for {}", eventType, e);
            }
        }, eventThreadPoolExecutor);  // Dedicated thread pool
    }
}
```

### **Timeline Visualization:**

```
Thread 1 (API Request):
10:10:00.100 → ZipCredit API: setupLmsClient(APP123)
10:10:00.200 → Finflux API: createClient() → SUCCESS (client_id: CL123)
10:10:00.300 → INSERT INTO a_application_stage_tracker (LMS_CLIENT_SETUP)
10:10:00.310 → processTriggers() → Submit CREATE_LOAN_TL to thread pool
10:10:00.315 → Return HTTP 200 to client ✅

Thread 2 (Event Processor):
10:10:00.320 → CREATE_LOAN_TL event starts
10:10:00.330 → Acquire Redis lock: "CREATE_LOAN_TL:APP123"
10:10:00.340 → Check idempotency: Loan already created? NO
10:10:00.350 → Call Finflux: createLoan(CL123, APP123)
10:10:02.500 → Finflux response: SUCCESS (loan_id: LN456) [2.15s API call]
10:10:02.510 → Save loan_id in DB
10:10:02.520 → Release Redis lock
10:10:02.530 → INSERT INTO a_application_stage_tracker (CREATE_LOAN_TL_SUCCESS)
10:10:02.540 → processTriggers() → Submit LOAN_DISBURSAL to thread pool
10:10:02.545 → Event complete ✅

Thread 3 (Event Processor):
10:10:02.550 → LOAN_DISBURSAL event starts
... and so on
```

**Key Observations:**
- ✅ **API returns immediately** (10:10:00.315) - doesn't wait for loan creation
- ✅ **Loan creation runs in background** (Thread 2)
- ✅ **Cascading triggers** - CREATE_LOAN_TL_SUCCESS triggers LOAN_DISBURSAL
- ✅ **Non-blocking** - Each event runs in separate thread

---

## 4. CONCURRENCY CONTROL

### **Problem Statement:**

```
3 EC2 Instances of ZipCredit Service (Load Balanced)
↓
Same request hits multiple instances within 50ms

Instance 1 (10:10:00.100): Process LMS_CLIENT_SETUP → Trigger CREATE_LOAN_TL
Instance 2 (10:10:00.150): Process LMS_CLIENT_SETUP → Trigger CREATE_LOAN_TL
Instance 3 (10:10:00.200): Process LMS_CLIENT_SETUP → Trigger CREATE_LOAN_TL

Without concurrency control:
❌ 3 loans created in LMS for same application!
❌ 3 duplicate entries in DB
❌ 3 disbursal transactions
```

### **Solution: 3-Layer Protection**

**Layer 1: Distributed Lock (Redisson RLock)**

```java
// CreateLoanTLEventServiceImpl.java

public void process(ApplicationDetailsDTO applicationDetails) {
    String applicationId = applicationDetails.getApplicationId();
    
    // Choose lock type: REDIS (prod) or LOCAL (dev)
    CacheType cacheType = isRedisLockEnabled(tenantId) 
        ? CacheType.REDIS : CacheType.LOCAL;
    CacheUtility cacheUtility = cacheUtilityFactory.getCacheUtility(cacheType);
    
    String lockKey = "CREATE_LOAN_TL:" + applicationId;
    
    try {
        // Try to acquire lock (60 second timeout)
        if (cacheUtility.tryLock(60, lockKey)) {
            logger.info("Lock acquired for application_id: {}", applicationId);
            
            try {
                // CRITICAL SECTION: Only one instance executes this
                processCreateLoan(applicationDetails);
                
            } finally {
                // Always release lock
                cacheUtility.releaseLock(lockKey);
                logger.info("Lock released for application_id: {}", applicationId);
            }
        } else {
            // Another instance is already processing
            logger.error("Failed to acquire lock for application_id: {}. " +
                    "Another instance is processing this event.", applicationId);
        }
    } catch (Exception e) {
        logger.error("Unexpected error in lock management", e);
    }
}
```

**What Happens Across 3 Instances:**

```
Redis Server (Shared State):
┌───────────────────────────────────────────┐
│ Locks:                                    │
│ ┌────────────────────────────────────┐    │
│ │ "CREATE_LOAN_TL:APP123"            │    │
│ │ held_by: Instance1_Thread5         │    │
│ │ acquired_at: 10:10:00.330          │    │
│ │ ttl: 60 seconds                    │    │
│ └────────────────────────────────────┘    │
└───────────────────────────────────────────┘

Instance 1 (10:10:00.330):
tryLock("CREATE_LOAN_TL:APP123", 60s)
→ SUCCESS ✅ (first to acquire)
→ Process loan creation
→ Finflux API call (2 seconds)
→ Save loan_id
→ releaseLock("CREATE_LOAN_TL:APP123")

Instance 2 (10:10:00.350):
tryLock("CREATE_LOAN_TL:APP123", 60s)
→ FAILED ❌ (lock already held by Instance 1)
→ Log: "Another instance is processing"
→ Exit event

Instance 3 (10:10:00.400):
tryLock("CREATE_LOAN_TL:APP123", 60s)
→ FAILED ❌ (lock already held by Instance 1)
→ Log: "Another instance is processing"
→ Exit event

Result: Only Instance 1 creates the loan ✅
```

**Layer 2: Idempotency Check (Database Query)**

```java
private void processCreateLoan(ApplicationDetailsDTO dto) {
    String applicationId = dto.getApplicationId();
    
    // Check if loan already created (double-check after acquiring lock)
    LoanBean existingLoan = loanService.findByApplicationId(applicationId);
    
    if (existingLoan != null) {
        logger.info("Loan already created for application_id: {}. " +
                "Skipping duplicate creation.", applicationId);
        return;  // Idempotent exit
    }
    
    // Proceed with loan creation
    Response lmsResponse = lmsApiService.createLoan(applicationId);
    
    if (lmsResponse.isSuccess()) {
        // Save loan_id
        saveLoan(applicationId, lmsResponse.getLoanId());
        
        // Mark success
        applicationStatusServiceImpl.insertApplicationTracker(
            applicationId, 
            tenantId, 
            ApplicationStage.CREATE_LOAN_TL_SUCCESS
        );
    }
}
```

**Layer 3: Database Unique Constraint**

```sql
CREATE TABLE a_application_stage_tracker (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(255) NOT NULL,
    current_status VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Prevent duplicate success stages for same application
    UNIQUE KEY uk_app_status (application_id, current_status, is_active)
);
```

**If two instances somehow both bypass the lock:**

```java
// Instance 1 tries to insert
INSERT INTO a_application_stage_tracker 
VALUES ('APP123', 'CREATE_LOAN_TL_SUCCESS', true, NOW());
→ SUCCESS ✅

// Instance 2 tries to insert (50ms later)
INSERT INTO a_application_stage_tracker 
VALUES ('APP123', 'CREATE_LOAN_TL_SUCCESS', true, NOW());
→ ERROR: Duplicate entry 'APP123-CREATE_LOAN_TL_SUCCESS-1' for key 'uk_app_status'

// Code handles this gracefully
try {
    applicationTrackerService.insertApplicationTracker(...);
} catch (DuplicateKeyException e) {
    logger.info("Stage already inserted by another instance");
}
```

### **Why 3 Layers?**

| Scenario | Layer 1 (Lock) | Layer 2 (DB Check) | Layer 3 (Constraint) |
|----------|---------------|-------------------|---------------------|
| **Normal case** | ✅ Prevents execution | Not reached | Not reached |
| **Redis down** | ❌ Fails | ✅ Prevents duplicate | Backup |
| **Race condition** | ❌ Rare failure | ✅ Catches duplicate | Backup |
| **Lock expires early** | ❌ Both acquire | ✅ Catches duplicate | Backup |
| **All else fails** | ❌ | ❌ | ✅ DB constraint prevents corruption |

**Defense in Depth Strategy** - Multiple layers ensure no duplicates even if one layer fails.

---

## 5. WHY THIS DESIGN?

### **Q: Why async event processing instead of synchronous?**

**Benefits:**
1. ✅ **Non-blocking API** - User gets response immediately, doesn't wait for loan creation (2+ seconds)
2. ✅ **Fault isolation** - If loan creation fails, API request still succeeds (client can retry later)
3. ✅ **Scalability** - Dedicated thread pool for events, API threads not blocked
4. ✅ **Retry-friendly** - Failed events can be retried independently without affecting previous steps

**Trade-offs:**
- ❌ **Eventual consistency** - Loan might not be created immediately when API returns
- ❌ **Debugging complexity** - Event failures happen in background, need good logging
- ❌ **Requires monitoring** - Need to track event processing lag

**Why we chose this:**
- User experience > Strong consistency (user doesn't care if loan created in 1s vs 5s)
- Finflux API is slow (2-3 seconds), blocking API would timeout
- Retry logic is easier with async (resubmit to queue)

### **Q: Why distributed locks instead of database locks?**

**Comparison:**

| Aspect | Distributed Lock (Redisson) | DB Lock (SELECT FOR UPDATE) |
|--------|----------------------------|----------------------------|
| **Speed** | Sub-millisecond | 10-50ms (network + query) |
| **Scope** | Application-level (across instances) | Row-level (within transaction) |
| **Deadlocks** | Rare (TTL auto-release) | Possible (complex joins) |
| **Lock duration** | Long-running OK (Watchdog extends) | Short only (transaction timeout) |
| **DB load** | Zero | High (connection pool exhaustion) |
| **Failure mode** | Lock released after TTL | Lock held until restart |

**Why we chose Redisson:**
- ✅ Event processing takes 2-5 seconds (too long for DB lock)
- ✅ Need to lock across multiple DB operations (client creation + loan creation)
- ✅ Redis is already in use for caching (no new infrastructure)
- ✅ Watchdog feature prevents lock expiry during long API calls

**When DB locks are better:**
- ✅ Short transactions (<100ms)
- ✅ Single row updates
- ✅ Need ACID guarantees

### **Q: Why boolean flags in application_state instead of single current_state enum?**

**Comparison:**

| Approach | Single State Enum | Boolean Flags |
|----------|------------------|---------------|
| **Current state** | `state: "LOAN_CREATED"` | `is_loan_created: true`, `is_disbursed: false` |
| **Query: Is loan created?** | `SELECT * WHERE state >= 'LOAN_CREATED'` | `SELECT * WHERE is_loan_created = true` |
| **Partial retry** | Hard (state rollback complex) | Easy (only update failed flag) |
| **Concurrency** | High contention (single row) | Lower contention (separate flags) |
| **Audit trail** | Separate table needed | Built-in (each flag change tracked) |
| **Race conditions** | Possible (state overwrite) | Rare (flag changes idempotent) |

**Why we chose boolean flags:**
- ✅ **Retry-friendly** - Can retry document generation without affecting loan creation flag
- ✅ **Parallel processing** - Multiple events can update different flags concurrently
- ✅ **Idempotent** - Setting `is_loan_created: true` twice has same effect
- ✅ **Query flexibility** - Can find all applications where `is_eligible: true AND is_loan_created: false`

**When state enum is better:**
- ✅ **Simple linear flow** (draft → submitted → approved → rejected)
- ✅ **Mutual exclusivity** (only one state at a time)
- ✅ **UI state display** (show user "Your application is approved")

---

## 6. CROSS-QUESTIONS & ANSWERS

### **Q1: How do you handle event processing failures?**

**Answer:**
We use a **3-tier failure handling strategy**:

**Tier 1: Immediate Retries (For Transient Failures)**
- If event fails, mark status as FAILED in event_tracker table
- On next trigger (manual or cron), check if event already completed
- If not, retry event (locks prevent duplicate processing)

**Example:**
```
10:10:00 → CREATE_LOAN_TL event starts
10:10:01 → Finflux API timeout (503 Service Unavailable)
10:10:01 → Mark event_tracker: status=FAILED
10:10:01 → Release lock

10:15:00 → Manual retry triggered
10:15:01 → Check event_tracker: status=FAILED (not SUCCESS)
10:15:01 → Acquire lock
10:15:02 → Retry Finflux API → SUCCESS ✅
10:15:03 → Mark event_tracker: status=SUCCESS
```

**Tier 2: Scheduled Retries (For Longer Outages)**
- Cron job runs every 30 minutes
- Finds all FAILED events from last 2 days
- Resubmits to event queue

**Tier 3: Manual Intervention (For Permanent Failures)**
- Admin dashboard shows failed events
- Support team investigates root cause
- Can manually retry or fix data and retry

**Key Point:** We DON'T rollback. We retry the failed step while keeping all previous successful steps intact.

---

### **Q2: What if an event is triggered twice due to a race condition?**

**Answer:**
**Defense in Depth** - Multiple layers prevent duplicate execution:

**Layer 1: Distributed Lock**
- Only one instance can acquire lock at a time
- Other instances fail fast and log

**Layer 2: Idempotency Check**
- Inside lock, check if already completed
- Query: `SELECT * FROM event_tracker WHERE application_id = ? AND event_type = ? AND status = 'SUCCESS'`
- If found, exit early

**Layer 3: Database Constraint**
- Unique key on `(application_id, current_status, is_active)`
- If somehow both try to insert success, second insert fails with DuplicateKeyException

**Real Example:**
```
Instance 1 (10:10:00.100):
→ tryLock("CREATE_LOAN_TL:APP123") → SUCCESS
→ Check: Loan already created? NO
→ Create loan via Finflux API
→ Save loan_id
→ INSERT CREATE_LOAN_TL_SUCCESS → SUCCESS
→ Release lock

Instance 2 (10:10:00.150):
→ tryLock("CREATE_LOAN_TL:APP123") → FAILED (held by Instance 1)
→ Exit

If lock somehow fails (Redis network issue):
Instance 2 (10:10:00.150):
→ tryLock("CREATE_LOAN_TL:APP123") → FALSE (but continues due to bug)
→ Check: Loan already created? YES (Instance 1 just created)
→ Exit early (idempotent)
```

---

### **Q3: How do you track which events are pending vs completed?**

**Answer:**
We use the **event_tracker table** combined with **application_stage_tracker**:

**event_tracker table:**
```sql
CREATE TABLE event_tracker (
    application_id VARCHAR(255),
    event_type VARCHAR(50),        -- CREATE_LOAN_TL, LOAN_DISBURSAL, etc.
    status VARCHAR(20),             -- SUCCESS, FAILED, IN_PROGRESS
    data MEDIUMTEXT,                -- JSON response from event
    application_stage VARCHAR(100), -- Which stage triggered this
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**Query Patterns:**

**Find all failed events for an application:**
```sql
SELECT event_type, status, data, updated_at 
FROM event_tracker 
WHERE application_id = 'APP123' 
AND status = 'FAILED'
ORDER BY updated_at DESC;
```

**Find applications stuck in a stage:**
```sql
SELECT a.application_id, a.current_status, e.event_type, e.status
FROM a_application_stage_tracker a
LEFT JOIN event_tracker e ON a.application_id = e.application_id 
    AND e.event_type = 'CREATE_LOAN_TL'
WHERE a.current_status = 'LMS_CLIENT_SETUP'
AND (e.status IS NULL OR e.status = 'FAILED');
```

**Dashboard View:**
```
Application: APP123
├─ Stage: LMS_CLIENT_SETUP ✅ (completed at 10:10:00)
├─ Event: CREATE_LOAN_TL ❌ (failed at 10:10:01)
│   └─ Error: Finflux API timeout
├─ Stage: CREATE_LOAN_TL_SUCCESS ⏳ (pending)
└─ Event: LOAN_DISBURSAL ⏳ (not triggered yet)
```

---

### **Q4: What if the stage insert succeeds but trigger fails?**

**Answer:**
**This is a critical edge case we handle explicitly:**

**Scenario:**
```java
public boolean insertApplicationTracker(...) {
    try {
        saveCurrentStatus(...);        // ✅ Inserts LMS_CLIENT_SETUP into DB
        processTriggers(...);          // ❌ Exception thrown (network issue)
        return true;
    } catch (Exception e) {
        logger.error("Error in updating application tracker", e);
        return false;  // But DB commit already happened!
    }
}
```

**Problem:**
- Stage is in DB (LMS_CLIENT_SETUP)
- But CREATE_LOAN_TL event was never triggered
- Application is stuck!

**Solution: Recovery Mechanisms**

**Mechanism 1: Retry API**
```java
// Admin/Support can manually retry events
POST /api/v1/admin/retry-events
{
    "application_id": "APP123",
    "stage": "LMS_CLIENT_SETUP"
}

// This calls processTriggers() again
triggerService.process(null, ApplicationStage.LMS_CLIENT_SETUP, "APP123", 1);
```

**Mechanism 2: Scheduled Recovery Job**
```java
@Scheduled(cron = "0 */10 * * * *")  // Every 10 minutes
public void recoverStuckApplications() {
    // Find applications where stage exists but event doesn't
    List<String> stuckApps = applicationTrackerService.findStuckApplications();
    
    for (String appId : stuckApps) {
        ApplicationTrackerBean tracker = applicationTrackerService.selectApplicationTrackerLatest(appId);
        triggerService.process(null, tracker.getCurrentStatus(), appId, tenantId);
    }
}
```

**Mechanism 3: Manual Admin Action**
- Admin dashboard shows applications with "stage inserted but no event"
- Support team can manually trigger event

**Why this works:**
- Triggers are **idempotent** (can be called multiple times safely)
- Event processing checks if already completed before executing
- Locks prevent duplicate execution

---

### **Q5: How do you debug when an application is stuck in a stage?**

**Answer:**
**Systematic debugging approach:**

**Step 1: Check Stage History**
```sql
SELECT id, current_status, created_at 
FROM a_application_stage_tracker 
WHERE application_id = 'APP123' 
ORDER BY created_at DESC;
```

**Step 2: Check Event Status**
```sql
SELECT event_type, status, data, updated_at 
FROM event_tracker 
WHERE application_id = 'APP123' 
ORDER BY updated_at DESC;
```

**Step 3: Check Logs (Coralogix/Sentry)**
```
Filter: application_id=APP123 AND level=ERROR
Time range: Last 30 minutes

Look for:
- "Failed to acquire lock" → Another instance processing (wait)
- "Event processing failed" → Check root cause in data field
- "API timeout" → Finflux/Digio down (retry)
```

**Step 4: Check Redis Locks**
```bash
# Connect to Redis
redis-cli -h production-redis.example.com

# Check if lock exists
KEYS *APP123*

# Output:
1) "CREATE_LOAN_TL:APP123"

# Check lock details
TTL CREATE_LOAN_TL:APP123
# If > 0, lock is held
# If -1, lock is held permanently (bug!)
# If -2, lock doesn't exist
```

**Step 5: Check Application State Flags**
```sql
SELECT * FROM application_state WHERE los_application_id = 'APP123';

-- If is_loan_created = false but stage = CREATE_LOAN_TL_SUCCESS
-- → Data inconsistency, manual fix needed
```

**Common Issues:**

| Symptom | Root Cause | Fix |
|---------|-----------|-----|
| Stage inserted, no event | Trigger failed mid-execution | Manually retry event |
| Event FAILED in tracker | API timeout/error | Check event_tracker.data for error, retry |
| Lock held permanently | Instance crashed during processing | Delete lock from Redis |
| Stage skipped | Dependent stage not completed | Check dependent stages config |
| Duplicate loans | Lock not acquired | Check Redis connectivity |

---

### **Q6: Why not use Kafka or RabbitMQ for event-driven architecture?**

**Answer:**
**We considered message queues but chose in-process async for these reasons:**

**Pros of Our Approach (CompletableFuture + Thread Pool):**
- ✅ **Simpler** - No external infrastructure (Kafka cluster, Zookeeper)
- ✅ **Lower latency** - In-memory queue, no network hop
- ✅ **Easier debugging** - All logs in same service, same correlation ID
- ✅ **Transactional** - Can share DB connection with main transaction
- ✅ **Sufficient scale** - Thread pool handles 100-500 events/sec (our load: ~50/sec)

**Cons (Why Kafka would be better for higher scale):**
- ❌ **No durability** - If instance crashes, events in queue are lost
- ❌ **No backpressure** - Queue can overflow if processing is slow
- ❌ **Limited retry** - Cron-based retry, not built into queue
- ❌ **Single point of failure** - If event processing thread pool is blocked, all events stuck

**When we'd migrate to Kafka:**
- ✅ Event volume > 1000/sec (need horizontal scaling)
- ✅ Need guaranteed delivery (at-least-once semantics)
- ✅ Multiple consumers for same event (fanout pattern)
- ✅ Cross-service events (orchestration → loan-repayment → notification)

**Current mitigation for durability:**
- ✅ Event trigger is recorded in DB (stage insert + event_tracker)
- ✅ Scheduled recovery job resubmits failed events
- ✅ Manual retry API for critical events

---

### **Q7: How do you handle event ordering? What if LOAN_DISBURSAL runs before CREATE_LOAN_TL?**

**Answer:**
**Event ordering is enforced through stage dependencies:**

**Stage Dependency Configuration:**
```java
// TriggerServiceImpl.java

private List<EventConfig> getGpayTermLoanEventConfigList(Integer tenantId) {
    List<EventConfig> eventConfigList = new ArrayList<>();
    
    // Event 1: LMS_CLIENT_SETUP triggers CREATE_LOAN_TL
    eventConfigList.add(createEventConfig(
        from: ApplicationStage.LMS_CLIENT_SETUP,
        to: ApplicationStage.LMS_CLIENT_SETUP,  // On this stage
        eventType: EventType.CREATE_LOAN_TL,
        channelCode: "GPAY"
    ));
    
    // Event 2: CREATE_LOAN_TL_SUCCESS triggers LOAN_DISBURSAL
    eventConfigList.add(createEventConfig(
        from: ApplicationStage.CREATE_LOAN_TL_SUCCESS,
        to: ApplicationStage.CREATE_LOAN_TL_SUCCESS,  // On this stage
        eventType: EventType.LOAN_DISBURSAL,
        channelCode: "GPAY"
    ));
    
    return eventConfigList;
}
```

**Enforcement Mechanisms:**

**Mechanism 1: Stage-Based Trigger**
- LOAN_DISBURSAL event is ONLY triggered when CREATE_LOAN_TL_SUCCESS stage is inserted
- Cannot insert CREATE_LOAN_TL_SUCCESS without loan_id (foreign key constraint)
- Cannot trigger LOAN_DISBURSAL without CREATE_LOAN_TL_SUCCESS

**Mechanism 2: Idempotency Check**
```java
// LoanDisbursalEventServiceImpl.java

public void process(ApplicationDetailsDTO dto) {
    // Check prerequisite: Loan must exist
    LoanBean loan = loanService.findByApplicationId(dto.getApplicationId());
    
    if (loan == null) {
        logger.error("Cannot disburse loan: Loan not created for application_id: {}", 
                dto.getApplicationId());
        throw new LoanNotFoundException();
    }
    
    if (loan.getStatus().equals("DISBURSED")) {
        logger.info("Loan already disbursed, skipping");
        return;  // Idempotent
    }
    
    // Proceed with disbursal
    payoutService.disburse(loan.getLoanId());
}
```

**Mechanism 3: Database Foreign Keys**
```sql
CREATE TABLE loan (
    id BIGINT PRIMARY KEY,
    application_id VARCHAR(255) NOT NULL,
    loan_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'CREATED',
    FOREIGN KEY (application_id) REFERENCES application(application_id)
);

CREATE TABLE disbursal (
    id BIGINT PRIMARY KEY,
    loan_id VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2),
    FOREIGN KEY (loan_id) REFERENCES loan(loan_id)  -- Cannot insert without loan
);
```

**What if someone tries to force incorrect order?**

```java
// Attempt to trigger LOAN_DISBURSAL directly (bypassing CREATE_LOAN_TL)
triggerService.process(
    null, 
    ApplicationStage.LOAN_DISBURSAL,  // Wrong stage!
    "APP123", 
    1
);

// Result:
// 1. Lookup: No event configured for LOAN_DISBURSAL stage
// 2. No event is triggered
// 3. Application remains stuck

// Correct flow:
// 1. INSERT CREATE_LOAN_TL_SUCCESS stage
// 2. Trigger fires automatically
// 3. LOAN_DISBURSAL event runs
```

---

## 🎯 KEY TAKEAWAYS FOR INTERVIEW

1. **"State Machine" is actually a Progress Tracker**
   - Boolean flags (not enum states)
   - Monotonic (never rollback)
   - Audit trail in separate table

2. **Triggers are NOT database triggers**
   - Java code in same method as stage insert
   - Event config loaded from DB (partner + stage → events)
   - Async execution via CompletableFuture

3. **Concurrency via 3-layer defense**
   - Distributed lock (Redisson) - primary protection
   - Idempotency check (DB query) - secondary protection
   - DB unique constraint - last resort

4. **Why this design?**
   - Non-blocking API (user gets fast response)
   - Retry-friendly (partial failures OK)
   - Fault isolation (event failure doesn't affect API)
   - Scalable (thread pool + eventual consistency)

5. **Trade-offs accepted**
   - Eventual consistency (vs strong consistency)
   - Debugging complexity (async failures)
   - Need recovery mechanisms (cron + manual)

---

**Final Advice:** When explaining to interviewer, focus on the **WHY** (design decisions) and **TRADE-OFFS** (what you gave up), not just the **WHAT** (implementation details).
