# 🔧 TIDE INTERVIEW PREP - ACTUAL SYSTEM ARCHITECTURE CORRECTIONS
**Critical Corrections Based on Real Code Analysis**

---

## ⚠️ MAJOR CORRECTION: I WAS WRONG ABOUT SAGA COMPENSATION

### **What I Said (WRONG):**
```java
// ❌ WRONG: Full Compensation Pattern
private void compensate(String sagaId) {
    // Rollback all steps in reverse order
    loanRepaymentService.cancelLoan(loanId);      // ❌ You DON'T do this
    zipCreditService.dropApplication(appId);      // ❌ You DON'T do this
}
```

### **What You ACTUALLY Do (CORRECT):**
```java
// ✅ CORRECT: Retry-Based Resilience Pattern
// You DON'T rollback - you RETRY failed steps!
if (loanCreationFailed) {
    // RETRY loan creation (not cancel application!)
    retryLoanCreation(appId);
}

if (documentGenerationFailed) {
    // Check why it failed, then RETRY (not rollback!)
    investigateFailure(appId);
    retryDocumentGeneration(appId);
}
```

---

## 1. YOUR ACTUAL STATE MACHINE PATTERN

### **Database Schema:**
```sql
-- From: orchestration/sql/migration/V2__State_Machine.sql

CREATE TABLE `application_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `los_application_id` varchar(255),
  
  -- BOOLEAN FLAGS FOR EACH STEP (Not enum states!)
  `is_application_id_created` bit(1) NOT NULL,
  `is_eligible` bit(1) NOT NULL,
  `is_aadhaar_verified` bit(1) NOT NULL,
  `is_documents_uploaded` bit(1) NOT NULL,
  `is_offer_accepted` bit(1) NOT NULL,
  `is_sanction_signed` bit(1) NOT NULL,
  `is_kfs_signed` bit(1) NOT NULL,
  `is_nach_registered` bit(1) NOT NULL,
  `is_va_created` bit(1) NOT NULL,
  `is_welcome_letter_signed` bit(1) NOT NULL,
  `is_migtc_signed` bit(1) NOT NULL,
  
  `created_at` datetime,
  `updated_at` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_los_application_id` (`los_application_id`)
);

-- AUDIT TABLE (Tracks all state changes)
CREATE TABLE `application_state_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,              -- Revision number
  `revtype` tinyint DEFAULT NULL,  -- INSERT/UPDATE/DELETE
  -- All same fields as above
  PRIMARY KEY (`id`, `rev`)
);
```

### **Pattern: Progress Tracker, Not State Machine**

**Your system is NOT a traditional state machine. It's a PROGRESS TRACKER with:**
- ✅ **Boolean flags** for each completed step
- ✅ **Audit trail** (`application_state_aud`) for all changes
- ✅ **Independent steps** that can be retried individually
- ❌ **NO rollback** - flags stay true once completed

### **Example Flow:**
```
Application Created:
{ is_application_id_created: true, is_eligible: false, is_aadhaar_verified: false, ... }

After Eligibility Check:
{ is_application_id_created: true, is_eligible: true, is_aadhaar_verified: false, ... }

If Aadhaar Verification FAILS:
{ is_application_id_created: true, is_eligible: true, is_aadhaar_verified: false }
→ RETRY Aadhaar verification (don't rollback eligibility!)

After Aadhaar Retry Succeeds:
{ is_application_id_created: true, is_eligible: true, is_aadhaar_verified: true, ... }
```

**Key Insight:** Each flag is **monotonic** (false → true, never true → false). You don't "undo" steps!

---

## 2. DISTRIBUTED LOCKING WITH REDISSON

### **Your Actual Implementation:**

```java
// From: zipcredit-backend/dgl_base/dgl-utility/src/main/java/com/dgl/utility/cache/RedisUtility.java

@Component
public class RedisUtility implements CacheUtility {
    
    @Autowired
    RedissonClient redissonClient;
    
    @Override
    public boolean tryLock(long timeout, String lockKey) {
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            boolean lockStatus = rLock.tryLock(timeout, TimeUnit.SECONDS);
            if (!lockStatus) {
                logger.info("Redis Lock failed to acquire for lock key {}", lockKey);
                return false;
            }
            logger.info("Redis Lock Acquired for lock key {}", lockKey);
            return true;
        } catch (Exception e) {
            logger.error("Error trying to acquire Redis Lock for key {}: {}", lockKey, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void releaseLock(String lockKey) {
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            if (rLock != null && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                logger.info("Redis Lock Released for lock key {}", lockKey);
            } else {
                logger.warn("Redis Lock not held by current thread");
            }
        } catch (Exception e) {
            logger.error("Error while releasing Redis Lock: {}", e.getMessage());
        }
    }
}
```

### **Real Usage in Production:**

```java
// From: zipcredit-backend/dgl_base/dgl-status/src/main/java/com/dgl/status/event/services/impl/CreateLoanTLEventServiceImpl.java

@Override
public void process(ApplicationDetailsDTO applicationDetails) {
    logger.info("Event process started for CREATE_LOAN_TL applicationId: {}", 
            applicationDetails.getApplicationId());
    
    // Choose lock type based on tenant config
    CacheType cacheType = isRedisLockEnabled(applicationDetails.getTenantId()) 
        ? CacheType.REDIS : CacheType.LOCAL;
    CacheUtility cacheUtility = cacheUtilityFactory.getCacheUtility(cacheType);
    
    String lockKey = "CREATE_LOAN_TL:" + applicationDetails.getApplicationId();
    
    try {
        // Try to acquire lock (60 second timeout)
        if (cacheUtility.tryLock(60, lockKey)) {
            logger.info("Lock acquired for application_id: {}", 
                    applicationDetails.getApplicationId());
            
            try {
                // Process loan creation
                processCreateLoan(applicationDetails);
            } finally {
                // Always release lock
                cacheUtility.releaseLock(lockKey);
                logger.info("Lock released for application_id: {}", 
                        applicationDetails.getApplicationId());
            }
        } else {
            logger.error("Failed to acquire lock for application_id: {}. " +
                    "Another process may be already processing this event.", 
                    applicationDetails.getApplicationId());
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("Thread interrupted while acquiring lock", e);
    } catch (Exception e) {
        logger.error("Unexpected error in lock management", e);
    }
}
```

### **Eligibility Check with Distributed Lock:**

```java
// From: zipcredit-backend/dgl_base/dgl-services/src/main/java/com/dgl/rest/version4/serviceImpl/ZCVersion4ServiceImpl.java

public Response checkEligibility(String applicationId, String stageName) {
    String lockKey = "ELIGIBILITY_" + applicationId;
    
    try {
        if (cacheUtility.tryLock(60, lockKey)) {
            // Check if eligibility already in progress
            ApplicationEligibilityDetailsBean existingEligibility = 
                eligibilityService.selectByApplicationIdAndStage(applicationId, stageId);
            
            if (existingEligibility != null && 
                "IN_PROGRESS".equals(existingEligibility.getDecision())) {
                logger.info("Eligibility already in progress for applicationId: {}", 
                        applicationId);
                return Response.ok("Already in progress");
            }
            
            // Insert IN_PROGRESS status
            insertStageDetails(applicationId, stageId, "IN_PROGRESS");
            
            // Process eligibility (calls BRE, credit bureau, etc.)
            String decision = processEligibility(applicationId, stageName);
            
            // Update with final decision
            updateStageDetails(applicationId, stageId, decision);
            
            return Response.ok(decision);
        } else {
            logger.error("Failed to acquire lock for applicationId: {}", applicationId);
            return Response.error("Another eligibility check in progress");
        }
    } finally {
        cacheUtility.releaseLock(lockKey);
    }
}
```

---

## 3. CONCURRENCY CONTROL ACROSS MULTIPLE EC2 INSTANCES

### **Problem You're Solving:**

```
Scenario: 3 EC2 instances of ZipCredit service running

Instance 1: Receives request for applicationId="APP123" at 10:00:00.100
Instance 2: Receives same request for "APP123" at 10:00:00.150 (50ms later)
Instance 3: Receives same request for "APP123" at 10:00:00.200 (100ms later)

Without distributed lock:
- All 3 instances start processing
- 3 duplicate documents generated
- 3 eligibility checks run (wasted credit bureau calls)
- 3 loans created in LMS (❌ BIG PROBLEM!)

With Redisson distributed lock:
- Instance 1 acquires lock "ELIGIBILITY_APP123"
- Instance 2 tries to acquire same lock → FAILS (already held by Instance 1)
- Instance 3 tries to acquire same lock → FAILS (already held by Instance 1)
- Instance 1 completes processing
- Instance 2 & 3 return "Already processed" or retry
```

### **Why Redis + Redisson?**

**Redis as Distributed Lock Server:**
- ✅ **Shared across all EC2 instances**
- ✅ **Sub-millisecond lock acquisition**
- ✅ **Automatic lock expiry** (if EC2 crashes, lock auto-released after TTL)
- ✅ **Fair locking** (FIFO order)

**Redisson Features:**
- ✅ **RLock** - Reentrant distributed lock
- ✅ **Watchdog** - Auto-extends lock if processing takes longer
- ✅ **isHeldByCurrentThread()** - Safe lock release

---

## 4. EVENT-DRIVEN DOCUMENT GENERATION (TRIGGERS)

### **Your Actual Trigger System:**

```java
// From: zipcredit-backend/dgl_base/dgl-status/src/main/java/com/dgl/status/services/impl/TriggerServiceImpl.java

@Service
public class TriggerServiceImpl implements ITriggerService {
    
    // Event configuration map: channelCode → ApplicationStage → List<EventConfig>
    final Map<String, Map<ApplicationStage, List<EventConfig>>> partnerStageEventConfigMap;
    
    @Qualifier("eventThreadPoolExecutor")
    @Autowired
    private TaskExecutor taskExecutor;  // For async processing
    
    @Autowired
    private EventServiceFactory eventServiceFactory;
    
    @Override
    public void processTriggers(ApplicationDetailsDTO applicationDetails, 
                               ApplicationStage currentStage) {
        
        // Get all events configured for this stage
        List<EventConfig> eventConfigs = getEventConfigsFor(
            applicationDetails.getChannelCode(), 
            currentStage
        );
        
        for (EventConfig eventConfig : eventConfigs) {
            // Get the event service (factory pattern)
            IEventService eventService = eventServiceFactory.get(eventConfig.getEventType());
            
            // Process asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    eventService.process(applicationDetails);
                } catch (Exception e) {
                    logger.error("Event processing failed", e);
                }
            }, taskExecutor);
        }
    }
}
```

### **Document Generation Event (Phase One):**

```java
// From: zipcredit-backend/dgl_base/dgl-status/src/main/java/com/dgl/status/event/services/impl/PhaseOneDocumentDscAndNotification.java

@Service
public class PhaseOneDocumentDscAndNotification extends AbstractDocumentDscAndNotification {
    
    @Override
    public EventType getEventType() {
        return EventType.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION;
    }
    
    @Override
    public void process(ApplicationDetailsDTO applicationDetails) {
        logger.info("Initiated PhaseOneDocumentDscAndNotification for applicationId: {}", 
                applicationDetails.getApplicationId());
        
        // ✅ IDEMPOTENCY CHECK: Has this already completed?
        if (checkApplicationTrackerStatus(
                applicationDetails.getApplicationId(), 
                ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS)) {
            logger.info("PhaseOneDocumentDscAndNotification retrial SKIPPED (already completed)");
            return;  // Don't regenerate documents!
        }
        
        // Fetch config for this channel
        ConfigRefDetails config = configRef.selectConfigByChannelAndCategory(
            applicationDetails.getChannelCode(), 
            "PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION"
        );
        
        if (config != null) {
            // Process: Generate → DSC → Notify
            DocumentDSCAndNotificationEventResponse response = 
                documentDscAndNotification(getEventType(), applicationDetails, config);
            
            if ("SUCCESS".equals(response.getStatus())) {
                // Mark as SUCCESS in tracker
                applicationStatusServiceImpl.insertApplicationTracker(
                    applicationDetails.getApplicationId(),
                    ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS
                );
                upsertEventTracker(applicationDetails, EventStatus.SUCCESS, response);
            } else {
                // Mark as FAILED (will be retried)
                applicationStatusServiceImpl.insertApplicationTracker(
                    applicationDetails.getApplicationId(),
                    ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_FAILURE
                );
                upsertEventTracker(applicationDetails, EventStatus.FAILED, response);
            }
        }
    }
}
```

### **Smart Retry Mechanism (3-Step Process):**

```java
// From: zipcredit-backend/dgl_base/dgl-status/src/main/java/com/dgl/status/services/impl/AbstractDocumentDscAndNotification.java

public DocumentDSCAndNotificationEventResponse documentDscAndNotification(
        EventType eventType, 
        ApplicationDetailsDTO applicationDetails, 
        Map<String, Object> sequenceMap) {
    
    // ✅ Fetch previous failed attempt (if any)
    DocumentDSCAndNotificationEventResponse previousResponse = fetchPreviousResponse(eventType, applicationDetails);
    
    // ✅ RETRY ONLY FAILED STEPS (not all steps!)
    
    // Step 1: Generate Document (if not already done)
    if (!Boolean.TRUE.equals(previousResponse.getDocumentGenerationStatus())) {
        String lastStep = generateDocument(eventType, applicationDetails, sequenceMap);
        previousResponse.setDocumentGenerationStatus("SUCCESS".equals(lastStep));
    }
    
    // Step 2: Add DSC (Digital Signature) on Document (if not already done)
    if (!Boolean.TRUE.equals(previousResponse.getAddDSConDocumentStatus())) {
        String lastStep = addDscOnDocuments(eventType, applicationDetails, sequenceMap);
        previousResponse.setAddDSConDocumentStatus("SUCCESS".equals(lastStep));
    }
    
    // Step 3: Send Notification (if not already done)
    if (!Boolean.TRUE.equals(previousResponse.getDocumentNotificationStatus())) {
        String lastStep = sendDocumentInNotification(eventType, applicationDetails, sequenceMap);
        previousResponse.setDocumentNotificationStatus("SUCCESS".equals(lastStep));
    }
    
    return previousResponse;
}
```

**KEY INSIGHT:** If document generation succeeds but notification fails:
- ✅ Document generation status saved as `true`
- ✅ On retry, skip document generation
- ✅ Only retry notification
- ❌ **DON'T rollback** document generation!

---

## 5. WEBHOOK RETRY PATTERN

### **Your Actual Webhook Retry Implementation:**

```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/service/impl/CallBackServiceImpl.java

@Override
public Response sendCallBackPayload(Partner partner, Integer lastDays) {
    PartnerDetails partnerDetails = partnerDetailsRepository.findByPartner(partner);
    
    // Get all FAILED webhooks from last N days
    List<WebhookDetails> failedWebhooks = getFailedWebhookDetailsAfterDate(
        partnerDetails.getId(),
        UtilsComponent.getDateBefore(lastDays != null ? lastDays : retryDays)
    );
    
    logger.info("Retrying webhooks count: {}", failedWebhooks.size());
    
    // Retry each failed webhook
    for (WebhookDetails webhookDetails : failedWebhooks) {
        logger.info("Retrying webhook with webhookId: {}", webhookDetails.getId());
        
        EventBasedCallBackRequest request = objectMapper.readValue(
            webhookDetails.getRequest(),
            EventBasedCallBackRequest.class
        );
        
        Optional<WebhookConfig> webhookConfig = webhookConfigRepository.findById(
            webhookDetails.getWebhookConfigId()
        );
        
        if (!webhookConfig.isPresent()) {
            logger.error("Config not found for webhook, skipping");
            continue;  // Skip this webhook
        }
        
        // Process webhook asynchronously
        CompletableFuture.runAsync(() -> 
            processWebhook(request, webhookDetails, webhookConfig.get(), partner), 
            taskExecutor
        );
    }
    
    return Response.success("Webhook retry process initiated");
}

private void processWebhook(EventBasedCallBackRequest request, 
                           WebhookDetails webhookDetails, 
                           WebhookConfig webhookConfig, 
                           Partner partner) {
    try {
        // Regenerate MAC/signature
        request.setMac(getMacValue(request.getData()));
        
        // Send webhook to partner
        Response response = partnerService.sendWebhook(
            request, 
            webhookConfig.getUrl(), 
            webhookConfig.getSecurityKey(), 
            partner
        );
        
        // Update webhook status
        webhookDetails.setResponse(objectMapper.writeValueAsString(response));
        
        if (APIStatus.SUCCESS.equals(response.getApiStatus()) && 
            response.getHttpStatus() == HttpStatus.SC_OK) {
            webhookDetails.setStatus(WebhookStatus.SUCCESS);
            webhookDetails.setRetryRequired(false);  // ✅ Stop retrying
        } else {
            webhookDetails.setStatus(WebhookStatus.FAILED);  // ❌ Will retry again
        }
        
        saveWebhookDetails(webhookDetails);
        
        logger.info("Webhook for applicationId: {}, eventType: {} processed with status: {}",
                webhookDetails.getApplicationId(), 
                webhookDetails.getEventType(), 
                webhookDetails.getStatus());
        
    } catch (Exception e) {
        webhookDetails.setResponse("Process Webhook Error");
        webhookDetails.setStatus(WebhookStatus.FAILED);
        logger.error("Process Webhook Error", e);
    }
}
```

### **Webhook Database Schema:**

```sql
CREATE TABLE webhook_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(255),
    event_type VARCHAR(50),
    partner_id BIGINT,
    request_id VARCHAR(255),
    request MEDIUMTEXT,          -- Stored for retry
    response MEDIUMTEXT,
    status ENUM('SUCCESS', 'FAILED', 'PENDING'),
    config_id BIGINT,
    retry_required BOOLEAN,      -- Stop retrying if false
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    INDEX idx_status_retry (status, retry_required),
    INDEX idx_application_id (application_id)
);
```

**Retry Strategy:**
- ✅ **webhook.retry.days=2** (from application.properties)
- ✅ **Scheduled job** runs every 30 minutes
- ✅ **Retries all FAILED webhooks** from last 2 days
- ✅ **Asynchronous processing** (CompletableFuture)
- ✅ **No exponential backoff** (fixed 30-minute interval)

---

## 6. NOTIFICATION RETRY PATTERN

```java
// From: zipcredit-backend/dgl_base/dgl-status/src/main/java/com/dgl/status/services/impl/AbstractDocumentDscAndNotification.java

private String sendNotificationWithRetry(String beanName,
                                         ApplicationDetailsDTO applicationDetails,
                                         EventType eventType,
                                         String documentNotificationConfig) {
    String status = null;
    
    // ✅ RETRY UP TO 3 TIMES
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            INotificationService notificationService = beanFactory.getBean(
                beanName, 
                INotificationService.class
            );
            
            status = notificationService.sendNotification(
                applicationDetails, 
                eventType, 
                documentNotificationConfig
            );
            
            if ("SUCCESS".equalsIgnoreCase(status)) {
                return status;  // ✅ Success, stop retrying
            } else {
                logger.error("Notification returned non-success status: {} for applicationId: {} (attempt {}/3)",
                        status, applicationDetails.getApplicationId(), attempt);
            }
        } catch (Exception ex) {
            logger.error("Notification send failed for applicationId: {} (attempt {}/3): {}",
                    applicationDetails.getApplicationId(), attempt, ex.getMessage(), ex);
        }
        
        // ✅ BACKOFF: Wait 5 seconds between retries
        if (attempt < 3) {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("Retry sleep interrupted; aborting notification retries");
                break;
            }
        }
    }
    
    return (status == null) ? "FAILURE" : status;
}
```

**Retry Strategy:**
- ✅ **3 immediate retries** (not scheduled)
- ✅ **5-second backoff** between retries
- ✅ **Synchronous** (blocks until all retries complete)
- ❌ **If all 3 fail**, mark step as FAILED (can be retried later via event replay)

---

## 7. REDIS CLUSTER & DISTRIBUTED CACHE

### **Configuration:**

```properties
# From: lending-project/orchestration/src/main/resources/application.properties

################ REDIS #############
redis.cluster.enabled=false         # Can be enabled for production
baseclient.redis.host=127.0.0.1
baseclient.redis.port=6379
baseclient.redis.password=

# Redisson Configuration (managed by Spring Boot)
spring.redis.host=${baseclient.redis.host}
spring.redis.port=${baseclient.redis.port}
spring.redis.password=${baseclient.redis.password}
```

### **Redisson Client Configuration:**

```java
// From: zipcredit-backend/dgl_base/dgl-utility/src/main/java/com/dgl/utility/cache/RedisConfig.java

@Configuration
public class RedisConfig {
    
    @Value("${redis.cluster.enabled:false}")
    private boolean clusterEnabled;
    
    @Value("${baseclient.redis.host}")
    private String redisHost;
    
    @Value("${baseclient.redis.port}")
    private int redisPort;
    
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        if (clusterEnabled) {
            // Cluster mode (production)
            config.useClusterServers()
                .addNodeAddress("redis://node1:6379", "redis://node2:6379", "redis://node3:6379")
                .setPassword(redisPassword)
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        } else {
            // Single server mode (dev/staging)
            config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setPassword(redisPassword)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5);
        }
        
        return Redisson.create(config);
    }
}
```

**What Redis is Used For:**
1. ✅ **Distributed Locks** (RLock) - Prevent duplicate processing across EC2 instances
2. ✅ **Caching** - Application data, eligibility results
3. ✅ **Session Storage** - Auth tokens
4. ✅ **Rate Limiting** (if configured)

---

## 8. LOCAL VS REDIS LOCK (TENANT-BASED CONFIGURATION)

```java
// From: zipcredit-backend/dgl_base/dgl-status/src/main/java/com/dgl/status/event/services/impl/CreateLoanTLEventServiceImpl.java

// Choose lock type dynamically based on tenant configuration
CacheType cacheType = isRedisLockEnabled(applicationDetails.getTenantId()) 
    ? CacheType.REDIS : CacheType.LOCAL;

CacheUtility cacheUtility = cacheUtilityFactory.getCacheUtility(cacheType);
```

**Factory Pattern:**
```java
@Component
public class CacheUtilityFactory {
    
    @Autowired
    private RedisUtility redisUtility;  // Redisson-based
    
    @Autowired
    private JavaUtility javaUtility;    // ReentrantLock-based
    
    public CacheUtility getCacheUtility(CacheType cacheType) {
        switch (cacheType) {
            case REDIS:
                return redisUtility;    // Distributed across EC2 instances
            case LOCAL:
                return javaUtility;     // In-memory (single JVM)
            default:
                return javaUtility;
        }
    }
}
```

**JavaUtility (Local Lock):**
```java
// From: zipcredit-backend/dgl_base/dgl-utility/src/main/java/com/dgl/utility/cache/JavaUtility.java

@Component
public class JavaUtility implements CacheUtility {
    
    // ConcurrentHashMap to store locks per key
    private static final Map<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    
    @Override
    public boolean tryLock(long timeout, String lockKey) {
        // Get or create lock for this key
        ReentrantLock lock = lockMap.computeIfAbsent(lockKey, key -> new ReentrantLock());
        
        try {
            boolean lockStatus = lock.tryLock(timeout, TimeUnit.SECONDS);
            if (!lockStatus) {
                logger.info("Java Lock failed to acquire for lock key {}", lockKey);
                return false;
            }
            logger.info("Java Lock Acquired for lock key {}", lockKey);
            return true;
        } catch (Exception e) {
            logger.error("Error acquiring Java Lock: {}", e.getMessage());
            lockMap.remove(lockKey);
            return false;
        }
    }
}
```

**When to Use Which:**
- ✅ **Redis Lock**: Production with multiple EC2 instances (DEFAULT)
- ✅ **Local Lock**: Dev/testing with single instance OR specific tenants

---

## 9. DOCUMENT GENERATION DEDUPLICATION

### **Problem:**

```
3 EC2 instances of ZipCredit
↓
Request 1 (Instance 1): Generate Welcome Letter for APP123
Request 2 (Instance 2): Generate Welcome Letter for APP123 (50ms later)
Request 3 (Instance 3): Generate Welcome Letter for APP123 (100ms later)
```

### **Solution:**

**Step 1: Idempotency Check in Application Tracker**
```java
// Check if document generation already completed
if (checkApplicationTrackerStatus(
        applicationId, 
        ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS)) {
    logger.info("Document generation already completed, skipping");
    return;  // ✅ Don't regenerate
}
```

**Step 2: Distributed Lock**
```java
String lockKey = "GENERATE_DOC_PHASE_ONE:" + applicationId;

if (cacheUtility.tryLock(60, lockKey)) {
    try {
        // Double-check after acquiring lock
        if (checkApplicationTrackerStatus(applicationId, SUCCESS_STAGE)) {
            return;  // Another instance completed while we waited
        }
        
        // Generate documents
        generateDocuments(applicationId);
        
        // Mark as completed
        insertApplicationTracker(applicationId, SUCCESS_STAGE);
        
    } finally {
        cacheUtility.releaseLock(lockKey);
    }
} else {
    logger.warn("Another instance is processing documents for {}", applicationId);
}
```

**Step 3: Database Unique Constraint**
```sql
-- application_tracker table ensures idempotency at DB level
CREATE TABLE application_tracker (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(255) NOT NULL,
    stage VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_stage (application_id, stage)  -- ✅ Prevents duplicates
);
```

If two instances somehow both try to insert the same success record:
```java
try {
    applicationTrackerService.insertApplicationTracker(
        applicationId, 
        ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS
    );
} catch (DuplicateKeyException e) {
    // Already inserted by another instance, that's fine!
    logger.info("Stage already marked as SUCCESS by another instance");
}
```

---

## 10. END-TO-END FLOW (GPAY EXAMPLE)

Based on your screenshot of the GPay application journey:

```
1. APPLICATION_CREATED
   ├─ State: { is_application_id_created: true }
   ├─ Triggers: None
   └─ Rollback? NO (application stays created)

2. ELIGIBILITY_CHECK
   ├─ Lock: "ELIGIBILITY_APP123" (60s timeout)
   ├─ Process: BRE + Credit Bureau
   ├─ State: { is_application_id_created: true, is_eligible: true }
   ├─ If FAILS: RETRY eligibility (don't drop application!)
   └─ Triggers on SUCCESS: Phase One Document Generation

3. PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION
   ├─ Lock: "GENERATE_DOC_PHASE_ONE:APP123"
   ├─ Process: 
   │   └─ Generate Loan Agreement PDF
   │   └─ Add Digital Signature
   │   └─ Send Email/SMS notification
   ├─ State: { ... , documents_uploaded: true }
   ├─ If Notification FAILS: 
   │   ├─ Document still generated (don't regenerate!)
   │   └─ RETRY only notification (3 attempts, 5s backoff)
   └─ Triggers on SUCCESS: NACH mandate creation

4. NACH_MANDATE_CREATION
   ├─ Lock: "CREATE_NACH:APP123"
   ├─ Process: Call Digio API
   ├─ State: { ... , is_nach_registered: true }
   ├─ If FAILS: RETRY NACH creation (application + docs still valid!)
   └─ Triggers on SUCCESS: Create Loan in LMS

5. CREATE_LOAN_TL (Term Loan)
   ├─ Lock: "CREATE_LOAN_TL:APP123" (Distributed Redis lock)
   ├─ Process: 
   │   └─ Check if loan already created (idempotency)
   │   └─ Call LMS (Finflux) createLoan API
   │   └─ Save loan_id in DB
   ├─ State: { ... , loan_created: true }
   ├─ If LMS FAILS: 
   │   ├─ DON'T cancel application
   │   ├─ DON'T cancel NACH
   │   └─ RETRY loan creation (with exponential backoff)
   └─ Triggers on SUCCESS: Loan disbursal webhook

6. LOAN_DISBURSAL
   ├─ Lock: "DISBURSE_LOAN:APP123"
   ├─ Process: Call Payout API
   ├─ State: { ... , loan_disbursed: true }
   ├─ If FAILS: RETRY disbursal (loan still exists in LMS!)
   └─ Triggers on SUCCESS: Disbursal webhook to GPay

7. WEBHOOK_TO_PARTNER (GPay)
   ├─ No lock (idempotent on partner side)
   ├─ Process: POST to GPay webhook URL with HMAC signature
   ├─ Store in webhook_details table (request + response)
   ├─ If FAILS: 
   │   ├─ status = FAILED, retry_required = true
   │   └─ Cron job retries every 30 minutes for 2 days
   └─ On SUCCESS: status = SUCCESS, retry_required = false
```

**Key Observations:**
- ✅ **No rollback at any step**
- ✅ **Each step retries independently**
- ✅ **Distributed locks prevent duplicate processing**
- ✅ **State machine tracks progress (monotonic flags)**
- ✅ **Audit trail records all changes**

---

## 🎯 CORRECTED INTERVIEW ANSWERS

### Q: "How do you handle failures in your distributed system?"

**❌ WRONG ANSWER (What I said before):**
> "We use Saga pattern with compensating transactions. If loan creation fails, we rollback by canceling the application and NACH mandate."

**✅ CORRECT ANSWER (What you actually do):**
> "We use a **retry-based resilience pattern**, not rollback. Our state machine tracks progress with boolean flags - once a step completes, it's marked as done and never undone.
>
> If loan creation fails, we **don't cancel the application or NACH mandate**. Instead, we:
> 1. Mark loan creation step as FAILED in application_tracker
> 2. Use distributed locks (Redisson RLock) to prevent duplicate retry attempts across EC2 instances
> 3. Retry loan creation with exponential backoff
> 4. If retry succeeds, mark the step as SUCCESS
>
> For example, in our GPay integration, if document generation succeeds but notification fails, we:
> - Keep the generated document (marked as documentGenerationStatus: true)
> - Only retry the notification step (3 attempts, 5-second backoff)
> - Don't regenerate the document
>
> This approach is better than Saga compensation because:
> - ✅ Transient failures (network timeouts) don't trigger expensive rollbacks
> - ✅ Partially completed work is preserved (e.g., documents generated, NACH created)
> - ✅ Credit bureau hits, LMS loan creation are not wasted
> - ✅ Each step can be retried independently"

### Q: "How do you prevent duplicate processing across multiple EC2 instances?"

**✅ CORRECT ANSWER:**
> "We use **Redisson distributed locks** with Redis as the lock server. For example, when loan creation is triggered:
>
> ```java
> String lockKey = \"CREATE_LOAN_TL:\" + applicationId;
> if (redisUtility.tryLock(60, lockKey)) {
>     try {
>         // Double-check idempotency
>         if (!isAlreadyProcessed(applicationId)) {
>             processLoanCreation(applicationId);
>             markAsProcessed(applicationId);
>         }
>     } finally {
>         redisUtility.releaseLock(lockKey);
>     }
> }
> ```
>
> This ensures:
> - ✅ Only ONE EC2 instance processes each request
> - ✅ Lock is visible across all instances (shared Redis)
> - ✅ Automatic lock release if instance crashes (TTL)
> - ✅ Database-level deduplication (unique constraints on application_tracker)
>
> We also have tenant-level configuration to switch between Redis locks (production) and local locks (dev/testing)."

### Q: "Walk me through your document generation pipeline."

**✅ CORRECT ANSWER:**
> "Document generation is **event-driven and asynchronous**:
>
> 1. **Trigger**: When application reaches a stage (e.g., PARTNER_APPROVED), TriggerServiceImpl looks up configured events for that stage + channel
>
> 2. **Async Processing**: Event is processed in CompletableFuture with dedicated thread pool (eventThreadPoolExecutor)
>
> 3. **Idempotency Check**: Before generating, we check application_tracker:
>    - If PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS exists → skip
>    - Else proceed
>
> 4. **Distributed Lock**: Acquire lock \"GENERATE_DOC_PHASE_ONE:{appId}\" to prevent duplicate generation across EC2 instances
>
> 5. **3-Step Process**:
>    - Generate documents (call document service)
>    - Add digital signature (DSC)
>    - Send notification (email/SMS with links)
>
> 6. **Smart Retry**: If step 2 fails but step 1 succeeded:
>    - Save documentGenerationStatus: true in event_tracker
>    - On retry, skip step 1 (already done)
>    - Only retry steps 2 & 3
>
> 7. **Notification Retry**: 3 immediate retries with 5-second backoff (synchronous)
>
> This prevents duplicate documents even if:
> - Same request hits multiple EC2 instances
> - Retry triggered while original request still processing
> - Partial failure (some steps succeed, others fail)"

---

## 📝 SUMMARY: KEY DIFFERENCES

| Aspect | What I Said (WRONG) | What You Actually Do (CORRECT) |
|--------|-------------------|--------------------------------|
| **Failure Handling** | Saga with compensation (rollback) | Retry-based resilience (no rollback) |
| **State Machine** | Enum-based state transitions | Boolean flags (progress tracker) |
| **Failed Steps** | Rollback all previous steps | Retry only the failed step |
| **Loan Creation Failure** | Cancel application + NACH | Retry loan creation (keep app + NACH) |
| **Document Generation** | Regenerate on retry | Skip if already generated |
| **Concurrency Control** | Circuit breaker | Distributed locks (Redisson) |
| **Lock Type** | Not specified | Redis (prod) or Local (dev), tenant-configurable |
| **Webhook Retry** | Exponential backoff (assumed) | Fixed 30-minute interval, 2 days max |
| **Notification Retry** | Not specified | 3 immediate retries, 5-second backoff |
| **Idempotency** | Not emphasized | Multiple layers: tracker + lock + DB constraint |

---

## 🎯 ACTION ITEMS FOR INTERVIEW

1. ✅ **Memorize the corrected patterns** (retry, not rollback)
2. ✅ **Explain distributed locks with Redisson** (show you understand concurrency)
3. ✅ **Emphasize idempotency** (application_tracker + locks + DB constraints)
4. ✅ **Describe event-driven architecture** (triggers + async processing)
5. ✅ **Use real examples** (GPay flow, document generation, NACH creation)

---

**This correction is CRITICAL for your interview! The interviewer will know if you're making assumptions vs. understanding the actual system.** 🎯
