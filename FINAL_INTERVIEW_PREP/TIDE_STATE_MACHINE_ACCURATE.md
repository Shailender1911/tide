# 🎯 STATE MACHINE - ACCURATE EXPLANATION (Based on Actual Code)

**For Tide Interview - How Our Event-Driven State Tracking Actually Works**

---

## 📋 QUICK SUMMARY

Our system is NOT a traditional state machine with boolean flags. It's an **event-driven state tracking system** with:

1. **A predefined enum of ~150+ stages** (`ApplicationStage.java`)
2. **A history table** (`a_application_stage_tracker`) that records every stage change
3. **A trigger system** (`TriggerServiceImpl.java`) that fires events when specific stages are reached
4. **Partner-specific configuration** that defines which events fire for which stages

---

## 🏗️ THE CORE COMPONENTS

### **Component 1: ApplicationStage Enum (Predefined Stages)**

```java
// File: com.dgl.common.enums.ApplicationStage.java
public enum ApplicationStage {
    // Application Lifecycle (~15 stages)
    CREATED,
    APPLICANT_DETAIL_UPDATED,
    COMPANY_DETAIL_UPDATED,
    LOAN_DETAIL_UPDATED,
    
    // Eligibility Stages (~8 stages)
    SOFT_ELIGIBILITY_IN_PROGRESS,
    SOFT_ELIGIBILITY_APPROVED,
    SOFT_ELIGIBILITY_DECLINED,
    FINAL_ELIGIBILITY_IN_PROGRESS,
    FINAL_ELIGIBILITY_APPROVED,
    FINAL_ELIGIBILITY_DECLINED,
    
    // KYC Stages (~30 stages)
    CKYC_PULLED,
    CKYC_FAILED,
    OKYC_OTP_SENT,
    OKYC_OTP_ACCEPTED,
    SELFIE_UPLOADED,
    SELFIE_MATCH_SUCCESS,
    SELFIE_MATCH_FAILED,
    DIGILOCKER_OKYC_FILE_UPLOADED,
    DIGILOCKER_OKYC_MATCH_SUCCESS,
    
    // Document Stages (~40 stages)
    SANCTION_KFS_GENERATED,
    SANCTION_SIGNED,
    KFS_SIGNED,
    LOA_GENERATED,
    LOA_SIGNED,
    MITC_SIGNED,
    
    // NACH/Mandate Stages (~10 stages)
    API_MANDATE_GENERATED,
    API_MANDATE_SUCCESS,
    UPI_MANDATE_SUCCESS,
    PHYSICAL_MANADATE_SUCCESS,
    NACH_MANDATE_DETAILS_UPDATED,
    
    // Approval & LMS Stages (~15 stages)
    APPLICATION_APPROVED,
    APPLICATION_DECLINED,
    LMS_CLIENT_SETUP_COMPLETED,
    LMS_CLIENT_SETUP_RETRY,
    
    // Loan Stages (~10 stages)
    LOAN_REQUEST_SUCCESS,
    LOAN_REQUEST_FAILED,
    UTR_RECIEVED,
    LOAN_DISBURSED,
    LOAN_CLOSED,
    
    // Document Generation Phases
    PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS,
    PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS,
    // ... and more (~150+ total stages)
}
```

**Key Point:** This enum defines ALL possible states an application can be in. It's a **fixed, predefined list** - not generated dynamically.

---

### **Component 2: The History Table (a_application_stage_tracker)**

```sql
CREATE TABLE a_application_stage_tracker (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id VARCHAR(255),
    prev_status VARCHAR(100),       -- Previous stage
    current_status VARCHAR(100),    -- Current stage (from ApplicationStage enum)
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    
    INDEX idx_app_status (application_id, current_status, is_active)
);
```

**Example Data:**
```
┌────┬──────────┬───────────────────────┬───────────────────────────────────────────────────┬───────────┐
│ ID │ app_id   │ prev_status           │ current_status                                    │ is_active │
├────┼──────────┼───────────────────────┼───────────────────────────────────────────────────┼───────────┤
│ 1  │ APP123   │ NULL                  │ CREATED                                           │ true      │
│ 2  │ APP123   │ CREATED               │ APPLICANT_DETAIL_UPDATED                          │ true      │
│ 3  │ APP123   │ APPLICANT_DETAIL_...  │ SOFT_ELIGIBILITY_APPROVED                         │ true      │
│ 4  │ APP123   │ SOFT_ELIGIBILI...     │ OFFERS_ACCEPTED                                   │ true      │
│ 5  │ APP123   │ OFFERS_ACCEPTED       │ SELFIE_MATCH_SUCCESS                              │ true      │
│ 6  │ APP123   │ SELFIE_MATCH_...      │ APPLICATION_APPROVED                              │ true      │
│ 7  │ APP123   │ APPLICATION_APPR...   │ PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION     │ true      │
│ 8  │ APP123   │ PHASE_ONE_DOC...      │ PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS │ true  │
│ 9  │ APP123   │ PHASE_ONE_DOC_S...    │ PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION     │ true      │
│ 10 │ APP123   │ PHASE_TWO_DOC...      │ PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS │ true  │
│ 11 │ APP123   │ PHASE_TWO_DOC_S...    │ LMS_CLIENT_SETUP_COMPLETED                        │ true      │
│ 12 │ APP123   │ LMS_CLIENT_SET...     │ CREATE_LOAN_TL_SUCCESS                            │ true      │
└────┴──────────┴───────────────────────┴───────────────────────────────────────────────────┴───────────┘
```

**How to Query:**
```sql
-- Get current status (latest row)
SELECT current_status 
FROM a_application_stage_tracker
WHERE application_id = 'APP123' AND is_active = true
ORDER BY updated_at DESC 
LIMIT 1;

-- Check if a specific stage was completed
SELECT COUNT(*) > 0 AS is_completed
FROM a_application_stage_tracker
WHERE application_id = 'APP123' 
  AND current_status = 'APPLICATION_APPROVED'
  AND is_active = true;

-- Get full journey
SELECT current_status, created_at
FROM a_application_stage_tracker
WHERE application_id = 'APP123'
ORDER BY created_at;
```

---

### **Component 3: EventConfig (Trigger Configuration)**

```java
// File: com.dgl.status.models.EventConfig.java
@Data
@Builder
public class EventConfig {
    private ApplicationStage from;        // (Optional) Previous stage
    private ApplicationStage to;          // Stage that triggers this event
    private boolean isAsync;              // Run in background thread?
    private List<ApplicationStage> required;  // Dependencies that must be completed
    private EventType eventType;          // Which event service to invoke
    private String channelCode;           // Which partner (GPAY, MEESHO, etc.)
}
```

**Example Configurations (from TriggerServiceImpl):**

```java
// When APPLICATION_APPROVED is inserted → trigger PHASE_ONE document generation
createEventConfig(
    null,                                              // from (any previous stage)
    ApplicationStage.APPLICATION_APPROVED,             // to (trigger stage)
    true,                                              // isAsync = true (run in background)
    null,                                              // required (no dependencies)
    EventType.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    "GPAYTL"                                           // channelCode
);

// When PHASE_ONE success → trigger PHASE_TWO
createEventConfig(
    null, 
    ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS, 
    true, 
    null,
    EventType.PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION, 
    "GPAYTL"
);

// When LMS_CLIENT_SETUP_COMPLETED → trigger CREATE_LOAN_TL
createEventConfig(
    null, 
    ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, 
    true, 
    null,
    EventType.CREATE_LOAN_TL, 
    "GPAYTL"
);
```

---

### **Component 4: EventType Enum (What Events Can Be Triggered)**

```java
// File: com.dgl.status.enums.EventType.java
public enum EventType {
    // Application Approval Events
    MARK_APPLICATION_APPROVED_GPAY,
    MARK_APPLICATION_APPROVED_MEESHO,
    MARK_APPLICATION_APPROVED_PHONEPE,
    MARK_APPLICATION_APPROVED_BHARATPE,
    
    // Document Generation Events
    PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    PHASE_THREE_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    PHASE_FOUR_DOCUMENTS_GENERATE_DSC_NOTIFICATION,
    
    // LMS/Loan Events
    LMS_CLIENT_SETUP,
    CREATE_LOAN,
    CREATE_LOAN_TL,
    
    // Callback Events
    PARTNER_APPLICATION_APPROVED_CALLBACK,
    KYC_SUCCESS_CALLBACK,
    
    // Utility Events
    GENERATE_CAM_REPORT,
    REGISTER_USER_FOR_UCIN,
    TRIGGER_SNAIL_MAIL,
    RISK_CATEGORIZATION,
    LOAN_MANDATE_ASSOCIATION,
    // ... total ~50 event types
}
```

Each EventType has a corresponding `IEventService` implementation:
```java
// Example: CreateLoanTLEventServiceImpl handles CREATE_LOAN_TL
@Component
public class CreateLoanTLEventServiceImpl implements IEventService {
    @Override
    public EventType getEventType() {
        return EventType.CREATE_LOAN_TL;
    }
    
    @Override
    public void process(ApplicationDetailsDTO applicationDetails) {
        // Business logic to create loan
    }
}
```

---

### **Component 5: TriggerServiceImpl (The Brain)**

This is where **which stage triggers which event** is configured:

```java
// File: TriggerServiceImpl.java

@Service
public class TriggerServiceImpl implements ITriggerService {
    
    // THE MASTER MAP: channelCode → (stage → list of events)
    final Map<String, Map<ApplicationStage, List<EventConfig>>> partnerStageEventConfigMap;
    
    // Build the map from partner-specific configurations
    private List<EventConfig> getTriggerEventConfigListForSP(Integer tenantId) {
        List<EventConfig> eventConfigList = new ArrayList<>();
        
        // Each partner has its own configuration
        eventConfigList.addAll(getMeeshoEventConfigList(tenantId));
        eventConfigList.addAll(getPhonePeEventConfigList(tenantId));
        eventConfigList.addAll(getBharatPeEventConfigList(tenantId));
        eventConfigList.addAll(getPaytmEventConfigList(tenantId));
        eventConfigList.addAll(getGpayTermLoanEventConfigList(tenantId));
        // ... more partners
        
        return eventConfigList;
    }
    
    // GPay-specific trigger configuration
    private List<EventConfig> getGpayTermLoanEventConfigList(Integer tenantId) {
        List<EventConfig> eventConfigList = new ArrayList<>();
        
        for (String channelCode : gpayChannelCodes.split(",")) {
            // When UPI_MANDATE_SUCCESS → Mark application approved
            eventConfigList.add(createEventConfig(null, 
                ApplicationStage.UPI_MANDATE_SUCCESS, true, null,
                EventType.MARK_APPLICATION_APPROVED_GPAY, channelCode));
            
            // When APPLICATION_APPROVED → Generate Phase 1 documents
            eventConfigList.add(createEventConfig(null, 
                ApplicationStage.APPLICATION_APPROVED, true, null,
                EventType.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION, channelCode));
            
            // When PHASE_ONE success → Generate Phase 2 documents
            eventConfigList.add(createEventConfig(null, 
                ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS, true, null,
                EventType.PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION, channelCode));
            
            // When PHASE_TWO success → Setup LMS Client
            eventConfigList.add(createEventConfig(null, 
                ApplicationStage.PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS, true, null,
                EventType.LMS_CLIENT_SETUP, channelCode));
            
            // When LMS_CLIENT_SETUP_COMPLETED → Create Loan
            eventConfigList.add(createEventConfig(null, 
                ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, true, null,
                EventType.CREATE_LOAN_TL, channelCode));
            
            // When LMS_CLIENT_SETUP_COMPLETED → Callback to partner
            eventConfigList.add(createEventConfig(null, 
                ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, true, null,
                EventType.PARTNER_APPLICATION_APPROVED_CALLBACK, channelCode));
            
            // ... 30+ more event configurations per partner
        }
        return eventConfigList;
    }
}
```

---

## 🔄 HOW THE TRIGGER SYSTEM WORKS (Complete Flow)

### **Step 1: Stage is Inserted**

When any process completes, it calls `insertApplicationTracker()`:

```java
// File: ApplicationStatusServiceImpl.java

@Override
public boolean insertApplicationTracker(String applicationId, 
                                        Integer tenantId,
                                        ApplicationStage currentStatus) {
    
    if (Objects.isNull(currentStatus)) return false;
    
    try {
        // 1. Get application details
        ApplicationBean application = getApplicationDetails(applicationId, tenantId);
        
        // 2. Handle duplicate stages (mark old one inactive if same stage exists)
        markCurrentStatusInActiveIfAlreadyAvailable(applicationId, tenantId, currentStatus);
        
        // 3. Get previous status
        String prevStatus = getPrevStatus(applicationId, tenantId);
        
        // 4. Disable dependent statuses if needed
        disablePreviousDependentStatuses(applicationId, tenantId, currentStatus, application);
        
        // 5. SAVE the new stage to database
        saveCurrentStatus(applicationId, tenantId, currentStatus, prevStatus);
        
        // 6. ⚡ PROCESS TRIGGERS (THIS IS THE KEY PART)
        processTriggers(applicationId, tenantId, currentStatus, prevStatus);
        
        // 7. Update admin case status
        updateAdminCaseStatus(applicationId, tenantId, currentStatus);
        
        // 8. Process partner status notifications
        processPartnerStatusAndNotification(application, currentStatus);
        
        return true;
    } catch (Exception e) {
        logger.error("Error in updating application tracker status for application_id {}", 
                     applicationId, e);
    }
    return false;
}
```

### **Step 2: Triggers are Processed**

```java
// File: ApplicationStatusServiceImpl.java

private void processTriggers(String applicationId, Integer tenantId, 
                            ApplicationStage currentStatus, String prevStatus) {
    try {
        ApplicationStage prevStage = EnumUtils.isValidEnum(ApplicationStage.class, prevStatus) 
            ? ApplicationStage.valueOf(prevStatus) 
            : null;
        
        // Delegate to TriggerServiceImpl
        triggerService.process(prevStage, currentStatus, applicationId, tenantId);
    } catch (Exception e) {
        logger.error("Error in processTriggers for application_id {}", applicationId, e);
    }
}
```

### **Step 3: TriggerServiceImpl Looks Up & Fires Events**

```java
// File: TriggerServiceImpl.java

@Override
public void process(ApplicationStage from, ApplicationStage to, 
                   String applicationId, Integer tenantId) {
    
    // 1. Get application and its channel code (partner)
    ApplicationBean application = getApplication(applicationId, tenantId);
    
    if (Objects.isNull(application) || Objects.isNull(application.getChannel_code())) {
        logger.error("Application or Channel Code not available for applicationId {}", applicationId);
        return;
    }
    
    String channelCode = application.getChannel_code();  // e.g., "GPAYTL"
    
    // 2. Initialize the trigger config map (lazy loading)
    initTriggerEventConfigList(tenantId, application);
    
    // 3. Check if any events are configured for this channel + stage
    if (Objects.isNull(partnerStageEventConfigMap.get(channelCode))
            || CollectionUtils.isEmpty(partnerStageEventConfigMap.get(channelCode).get(to))) {
        // No events configured for this combination
        return;
    }
    
    // 4. Process each configured event
    processEvents(from, to, applicationId, tenantId, application);
}

private void processEvents(ApplicationStage from, ApplicationStage to, 
                          String applicationId, Integer tenantId,
                          ApplicationBean application) {
    
    ApplicationDetailsDTO applicationDetails = null;
    String channelCode = application.getChannel_code();
    
    // Get all events configured for this channel + stage
    List<EventConfig> events = partnerStageEventConfigMap.get(channelCode).get(to);
    
    for (EventConfig eventConfig : events) {
        logger.info("Trigger event for eventType {} for application id {} current status {}", 
                    eventConfig.getEventType(), applicationId, to);
        
        // Lazy load application details (only once)
        if (Objects.isNull(applicationDetails)) {
            applicationDetails = prepareAndGetApplicationDetailsDTO(application, applicationId, tenantId);
            if (Objects.isNull(applicationDetails)) {
                logger.error("Error in executing trigger for application tracker status {} for application_id {}", 
                            to, applicationId);
                return;
            }
            applicationDetails.setCurrentApplicationStatus(to);
        }
        
        // Validate dependencies
        if (validateApplicationDetails(from, applicationDetails, eventConfig)) {
            
            // Get the event service for this event type
            IEventService eventService = eventServiceFactory.get(eventConfig.getEventType());
            
            // Execute: ASYNC or SYNC
            if (eventConfig.isAsync()) {
                // Run in background thread pool
                final ApplicationDetailsDTO appDetailsFinal = applicationDetails;
                CompletableFuture.runAsync(() -> eventService.process(appDetailsFinal), taskExecutor);
            } else {
                // Run synchronously (blocks current thread)
                eventService.process(applicationDetails);
            }
        } else {
            logger.info("Validation failed for application id {} current status{} skipping event for eventType {}", 
                       applicationId, to, eventConfig.getEventType());
        }
    }
}
```

### **Step 4: Dependency Validation**

```java
// File: TriggerServiceImpl.java

private boolean validateApplicationDetails(ApplicationStage from, 
                                          ApplicationDetailsDTO applicationDetails,
                                          EventConfig eventConfig) {
    
    // Build map of completed stages
    Map<ApplicationStage, ApplicationTrackerBean> completedStages = new EnumMap<>(ApplicationStage.class);
    
    if (CollectionUtils.isNotEmpty(applicationDetails.getApplicationTrackerList())) {
        for (ApplicationTrackerBean appTracker : applicationDetails.getApplicationTrackerList()) {
            if (EnumUtils.isValidEnum(ApplicationStage.class, appTracker.getCurrent_status())) {
                completedStages.put(
                    ApplicationStage.valueOf(appTracker.getCurrent_status()), 
                    appTracker
                );
            }
        }
    }
    
    // Check 1: If eventConfig specifies a "from" stage, verify it
    if (Objects.nonNull(eventConfig.getFrom()) 
            && !eventConfig.getFrom().equals(from)
            && !completedStages.containsKey(from)) {
        return false;
    }
    
    // Check 2: Verify all required stages are completed
    if (CollectionUtils.isNotEmpty(eventConfig.getRequired())) {
        for (ApplicationStage requiredStage : eventConfig.getRequired()) {
            if (!completedStages.containsKey(requiredStage)) {
                return false;  // Dependency not met
            }
        }
    }
    
    // Store completed stages for event processing
    applicationDetails.setCompletedStages(completedStages);
    
    return true;
}
```

---

## 📊 VISUAL FLOW DIAGRAM

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           HOW TRIGGERS WORK - COMPLETE FLOW                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐     ┌──────────────────────────────────────────────────────────────┐
│   Some Process   │     │           ApplicationStatusServiceImpl                        │
│   Completes      │────▶│   insertApplicationTracker(appId, tenantId, currentStatus)   │
│   (e.g., KYC)    │     │                                                              │
└──────────────────┘     │   1. saveCurrentStatus() → INSERT into a_application_stage_tracker │
                         │   2. processTriggers() ─────────────────────────────────────────┐
                         └──────────────────────────────────────────────────────────────┘  │
                                                                                           │
                                                                                           ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              TriggerServiceImpl.process()                                │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│  1. Get application's channel_code (e.g., "GPAYTL")                                     │
│                                                                                          │
│  2. Look up: partnerStageEventConfigMap.get("GPAYTL").get(currentStatus)                │
│                                                                                          │
│     ┌─────────────────────────────────────────────────────────────────────────┐         │
│     │ partnerStageEventConfigMap =                                             │         │
│     │   {                                                                      │         │
│     │     "GPAYTL": {                                                          │         │
│     │       APPLICATION_APPROVED: [                                            │         │
│     │         EventConfig(PHASE_ONE_DOCUMENTS_..., async=true),               │         │
│     │         EventConfig(REGISTER_USER_FOR_UCIN, async=true),                │         │
│     │         EventConfig(CREATE_CKYC_UPLOAD_ENTRY, async=true)               │         │
│     │       ],                                                                 │         │
│     │       LMS_CLIENT_SETUP_COMPLETED: [                                      │         │
│     │         EventConfig(CREATE_LOAN_TL, async=true),                        │         │
│     │         EventConfig(PARTNER_APPLICATION_APPROVED_CALLBACK, async=true)  │         │
│     │       ],                                                                 │         │
│     │       ...                                                                │         │
│     │     },                                                                   │         │
│     │     "MEESHO": { ... different config ... },                              │         │
│     │     "PHONEPE": { ... different config ... }                              │         │
│     │   }                                                                      │         │
│     └─────────────────────────────────────────────────────────────────────────┘         │
│                                                                                          │
│  3. For each EventConfig found:                                                          │
│     a. Validate dependencies (required stages completed?)                                │
│     b. Get IEventService from factory                                                    │
│     c. Execute: async → CompletableFuture.runAsync(...)                                 │
│                 sync  → eventService.process(...)                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                          IEventService Implementation                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│  Example: PhaseOneDocumentDscAndNotification                                             │
│                                                                                          │
│  public void process(ApplicationDetailsDTO applicationDetails) {                         │
│      String applicationId = applicationDetails.getApplicationId();                       │
│                                                                                          │
│      // 1. Idempotency check                                                             │
│      if (isAlreadyCompleted(applicationId, ApplicationStage.PHASE_ONE_..._SUCCESS)) {   │
│          logger.info("Already completed, skipping...");                                  │
│          return;                                                                         │
│      }                                                                                   │
│                                                                                          │
│      // 2. Acquire distributed lock                                                      │
│      if (redisUtility.tryLock(LOCK_TIMEOUT, "PHASE_ONE:" + applicationId)) {            │
│          try {                                                                           │
│              // 3. Do the work (generate documents, etc.)                                │
│              generateDocuments(applicationId);                                           │
│                                                                                          │
│              // 4. Insert SUCCESS stage → THIS TRIGGERS NEXT EVENT!                      │
│              applicationStatusService.insertApplicationTracker(                          │
│                  applicationId, tenantId,                                                │
│                  ApplicationStage.PHASE_ONE_DOCUMENTS_..._SUCCESS  // ← Triggers Phase 2 │
│              );                                                                          │
│          } finally {                                                                     │
│              redisUtility.releaseLock("PHASE_ONE:" + applicationId);                    │
│          }                                                                               │
│      }                                                                                   │
│  }                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔗 THE CASCADE EFFECT (GPay Example)

When an application is approved, here's how events cascade:

```
APPLICATION_APPROVED is inserted
    │
    ├──▶ Triggers: PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION (async)
    │           │
    │           ▼ (on success)
    │    PHASE_ONE_DOCUMENTS_..._SUCCESS is inserted
    │           │
    │           └──▶ Triggers: PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION (async)
    │                       │
    │                       ▼ (on success)
    │                PHASE_TWO_DOCUMENTS_..._SUCCESS is inserted
    │                       │
    │                       └──▶ Triggers: LMS_CLIENT_SETUP (async)
    │                                   │
    │                                   ▼ (on success)
    │                            LMS_CLIENT_SETUP_COMPLETED is inserted
    │                                   │
    │                                   ├──▶ Triggers: CREATE_LOAN_TL (async)
    │                                   │           │
    │                                   │           ▼ (on success)
    │                                   │    LOAN_REQUEST_SUCCESS is inserted
    │                                   │           │
    │                                   │           └──▶ Triggers: LOAN_MANDATE_ASSOCIATION
    │                                   │
    │                                   └──▶ Triggers: PARTNER_APPLICATION_APPROVED_CALLBACK (async)
    │
    ├──▶ Triggers: REGISTER_USER_FOR_UCIN (async)
    │
    ├──▶ Triggers: CREATE_CKYC_UPLOAD_ENTRY (async)
    │
    ├──▶ Triggers: RISK_CATEGORIZATION (async)
    │
    └──▶ Triggers: CREATE_APPLICATION_LRS (async)
```

---

## 🎯 HANDLING MULTIPLE TRIGGERS FOR SAME STAGE

**Question:** What if one stage triggers multiple events?

**Answer:** All events are fired! Look at this real config:

```java
// When LMS_CLIENT_SETUP_COMPLETED for GPay:
eventConfigList.add(createEventConfig(null, 
    ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, true, null,
    EventType.PARTNER_APPLICATION_APPROVED_CALLBACK, channelCode));  // Event 1

eventConfigList.add(createEventConfig(null, 
    ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, true, null,
    EventType.CREATE_LOAN_TL, channelCode));                         // Event 2
```

When `LMS_CLIENT_SETUP_COMPLETED` is inserted:
1. TriggerServiceImpl finds **BOTH** EventConfigs
2. **BOTH** events are fired in parallel (both are async=true)
3. Each runs in a separate thread from the thread pool

**Code that handles this:**
```java
for (EventConfig eventConfig : partnerStageEventConfigMap.get(channelCode).get(to)) {
    // This loop fires ALL events configured for this stage
    if (eventConfig.isAsync()) {
        CompletableFuture.runAsync(() -> eventService.process(appDetailsFinal), taskExecutor);
    } else {
        eventService.process(applicationDetails);
    }
}
```

---

## 🔒 HOW DUPLICATES ARE PREVENTED

### **Layer 1: Idempotency Check in Event Service**

```java
// Inside each event service
public void process(ApplicationDetailsDTO applicationDetails) {
    // Check if already completed
    if (checkApplicationTrackerStatus(applicationId, tenantId, 
            ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS)) {
        logger.info("Already completed, skipping...");
        return;  // ← Don't process again
    }
    // ... proceed with processing
}
```

### **Layer 2: Distributed Lock**

```java
String lockKey = "DOC_GEN:" + applicationId;
if (redisUtility.tryLock(LOCK_TIMEOUT, lockKey)) {
    try {
        // Only one instance can process at a time
        processDocumentGeneration(applicationId);
    } finally {
        redisUtility.releaseLock(lockKey);
    }
} else {
    logger.warn("Another instance is processing, skipping...");
}
```

### **Layer 3: Stage Deactivation in Tracker**

```java
// When same stage is inserted again, old one is marked inactive
private void markCurrentStatusInActiveIfAlreadyAvailable(String applicationId, 
                                                         Integer tenantId, 
                                                         ApplicationStage currentStatus) {
    // Mark existing row as inactive before inserting new one
}
```

---

## 🏢 PARTNER-SPECIFIC CONFIGURATIONS

**Why different configs per partner?**

Each partner has different business flows:

| Partner | Flow Difference |
|---------|----------------|
| **GPay** | UPI mandate → Approval → Loan creation |
| **Meesho** | Physical mandate OR CPV → Settlement account → Approval |
| **PhonePe** | NACH + Snail mail option → Approval |
| **BharatPe** | Partner approval flow → Our approval |

**Code structure:**
```java
// TriggerServiceImpl.java
private List<EventConfig> getTriggerEventConfigListForSP(Integer tenantId) {
    List<EventConfig> eventConfigList = new ArrayList<>();
    
    eventConfigList.addAll(getMeeshoEventConfigList(tenantId));      // 50+ configs
    eventConfigList.addAll(getPhonePeEventConfigList(tenantId));     // 40+ configs
    eventConfigList.addAll(getBharatPeEventConfigList(tenantId));    // 45+ configs
    eventConfigList.addAll(getPaytmEventConfigList(tenantId));       // 40+ configs
    eventConfigList.addAll(getGpayTermLoanEventConfigList(tenantId)); // 50+ configs
    // ... each partner has its own configuration method
    
    return eventConfigList;
}
```

---

## ❓ INTERVIEW Q&A

### **Q1: How do you decide which event to trigger when?**

**Answer:**
> "It's configuration-driven. We have a master map: `partnerStageEventConfigMap` which is `Map<channelCode, Map<ApplicationStage, List<EventConfig>>>`. 
>
> For each partner (GPay, Meesho, etc.), we configure which events should fire for which stage. This is done in partner-specific methods like `getGpayTermLoanEventConfigList()`.
>
> When a stage is inserted, `TriggerServiceImpl.process()` looks up this map:
> 1. Gets the application's channel code (e.g., 'GPAYTL')
> 2. Finds all EventConfigs for that channel + stage combination
> 3. Validates dependencies (required stages completed?)
> 4. Fires each event (async or sync)"

---

### **Q2: What if multiple events are configured for the same stage?**

**Answer:**
> "All of them fire! The map returns `List<EventConfig>`, and we iterate through all of them:
>
> ```java
> for (EventConfig eventConfig : partnerStageEventConfigMap.get(channelCode).get(to)) {
>     // Fire each event
> }
> ```
>
> For example, when `LMS_CLIENT_SETUP_COMPLETED` is inserted for GPay, we fire:
> - `CREATE_LOAN_TL` (to create the loan)
> - `PARTNER_APPLICATION_APPROVED_CALLBACK` (to notify GPay)
>
> Both run in parallel since they're configured as `isAsync=true`."

---

### **Q3: How do dependencies work?**

**Answer:**
> "Each EventConfig can have a `required` field - a list of stages that must be completed first.
>
> Before firing an event, we validate:
> ```java
> if (CollectionUtils.isNotEmpty(eventConfig.getRequired())) {
>     for (ApplicationStage requiredStage : eventConfig.getRequired()) {
>         if (!completedStages.containsKey(requiredStage)) {
>             return false;  // Dependency not met, don't fire
>         }
>     }
> }
> ```
>
> This ensures events only fire when their prerequisites are satisfied."

---

### **Q4: Async vs Sync - When do you use each?**

**Answer:**
> "Most events are async (`isAsync=true`) because:
> - User gets fast response (API returns immediately)
> - Events can be slow (external API calls)
> - Events can run in parallel
>
> Sync events (`isAsync=false`) are used when:
> - Result is needed immediately for next step
> - Event must complete before proceeding
>
> For example, `MARK_APPLICATION_APPROVED_GPAY` is sync because we need the approval result before proceeding."

---

### **Q5: How do you add a new event for a partner?**

**Answer:**
> "It's just adding a line to the partner's config method:
>
> ```java
> // In getGpayTermLoanEventConfigList()
> eventConfigList.add(createEventConfig(
>     null,                              // from (previous stage)
>     ApplicationStage.SOME_STAGE,       // to (trigger stage)
>     true,                              // isAsync
>     null,                              // required dependencies
>     EventType.NEW_EVENT_TYPE,          // event to fire
>     channelCode                        // partner
> ));
> ```
>
> Then implement the `IEventService` for the new event type. No workflow engine, no external service - just code."

---

## 🎯 KEY TAKEAWAYS

1. **NOT a traditional state machine** - It's an event-driven trigger system
2. **Single history table** - `a_application_stage_tracker` records all stages
3. **Predefined stages** - ~150+ stages in `ApplicationStage` enum
4. **Partner-specific configs** - Each partner has different event mappings
5. **Configuration in code** - No external workflow engine, all in `TriggerServiceImpl`
6. **Async by default** - Most events run in background threads
7. **Cascade effect** - Events trigger stages, which trigger more events
8. **Idempotent** - Multiple layers prevent duplicate processing

---

**Interview Strategy:**
1. Start with "We have an event-driven state tracking system"
2. Explain the 3 components: stages enum, history table, trigger service
3. Show how the map works: `channelCode → stage → events`
4. Explain cascade: one event's success triggers next stage
5. Discuss partner-specific configs
6. Mention duplicate prevention layers
