# 🎓 STATE MACHINE - COMPLETE END-TO-END EXPLANATION

**For Tide Interview - Exactly How Our System Works**

---

## 1. ENTRY POINT: How the Process Gets Invoked

Everything starts when any part of our system calls `ApplicationStatusServiceImpl.insertApplicationTracker()`:

```java
// ENTRY POINT - This is called from ANYWHERE in the system
@Override
public boolean insertApplicationTracker(String applicationId, Integer tenantId, 
                                         ApplicationStage currentStatus) {
    
    if(Objects.isNull(currentStatus)) return false;
    
    // Step 1: Load partner-specific dependent stages (for invalidation logic)
    initDependentStagesMap(applicationId, tenantId);
    
    try {
        // Step 2: Get application details (including channel_code like "GPAYTL", "MEESHO")
        ApplicationBean application = getApplicationDetails(applicationId, tenantId);
        
        // Step 3: If this status already exists for this app, mark it inactive first
        markcurrentStatusInActiveIfAlreadyAvailable(applicationId, tenantId, currentStatus);
        
        // Step 4: Get previous status for audit trail
        String prevStatus = getPrevStatus(applicationId, tenantId);
        
        // Step 5: Invalidate conflicting statuses (e.g., if APPROVED, disable DECLINED)
        disablePreviousDependentStatuses(applicationId, tenantId, currentStatus, application);
        
        // Step 6: INSERT the new status into a_application_stage_tracker
        saveCurrentStatus(applicationId, tenantId, currentStatus, prevStatus);
        
        // Step 7: 🔥 THIS IS WHERE TRIGGERS FIRE 🔥
        processTriggers(applicationId, tenantId, currentStatus, prevStatus);
        
        // Step 8: Update admin case status (for internal dashboard)
        updateAdminCaseStatus(applicationId, tenantId, currentStatus);
        
        // Step 9: Notify partner if needed (async)
        processPartnerStatusAndNotification(application, currentStatus);
        
        return true;
    } catch (Exception e) {
        logger.error("Error in updating application tracker for application_id {}", applicationId, e);
    }
    return false;
}
```

**Who calls this method?**
- KYC service after selfie match
- Document service after document signing
- Eligibility service after credit check
- Any service that completes a step in the loan journey

---

## 2. HOW TRIGGERS GET FIRED (processTriggers → TriggerServiceImpl)

```java
// In ApplicationStatusServiceImpl
private void processTriggers(String applicationId, Integer tenantId, 
                             ApplicationStage currentStatus, String prevStatus) {
    try {
        ApplicationStage prevStage = EnumUtils.isValidEnum(ApplicationStage.class, prevStatus) 
            ? ApplicationStage.valueOf(prevStatus) 
            : null;
        
        // Calls TriggerServiceImpl.process()
        triggerService.process(prevStage, currentStatus, applicationId, tenantId);
    } catch (Exception e) {
        logger.error("Exception in executing trigger for status {} for application_id {}", 
                    currentStatus, applicationId, e);
    }
}
```

---

## 3. INSIDE TriggerServiceImpl: THE BRAIN OF EVENT FIRING

### 3.1 The Master Data Structure

```java
// This map holds ALL partner configurations
// Structure: channelCode → (ApplicationStage → List<EventConfig>)
final Map<String, Map<ApplicationStage, List<EventConfig>>> partnerStageEventConfigMap;

// Example of what's inside:
// "GPAYTL" → {
//     APPLICATION_APPROVED → [EVENT_1, EVENT_2, EVENT_3],
//     LMS_CLIENT_SETUP_COMPLETED → [EVENT_4, EVENT_5],
//     PHASE_ONE_SUCCESS → [EVENT_6]
// }
// "MEESHO" → {
//     APPLICATION_APPROVED → [EVENT_7, EVENT_8],
//     ...
// }
```

### 3.2 How the Map Gets Populated (One-Time Initialization)

```java
private void initTriggerEventListForSP(Integer tenantId) {
    // Double-checked locking for thread safety
    if (partnerStageEventConfigMap.isEmpty()) {
        synchronized (this) {
            if (partnerStageEventConfigMap.isEmpty()) {
                
                // Collect ALL partner event configs
                getTriggerEventConfigListForSP(tenantId).stream().forEach(eventConfig -> {
                    
                    // Get or create partner's stage map
                    Map<ApplicationStage, List<EventConfig>> stageEventConfigMap = 
                        partnerStageEventConfigMap.getOrDefault(
                            eventConfig.getChannelCode(),  // e.g., "GPAYTL"
                            new EnumMap<>(ApplicationStage.class)
                        );
                    
                    // Get or create event list for this stage
                    List<EventConfig> eventConfigList = 
                        stageEventConfigMap.getOrDefault(
                            eventConfig.getTo(),  // e.g., APPLICATION_APPROVED
                            new ArrayList<>()
                        );
                    
                    // Add this event to the list
                    eventConfigList.add(eventConfig);
                    stageEventConfigMap.put(eventConfig.getTo(), eventConfigList);
                    partnerStageEventConfigMap.put(eventConfig.getChannelCode(), stageEventConfigMap);
                });
            }
        }
    }
}

// This collects configs from ALL partners
private List<EventConfig> getTriggerEventConfigListForSP(Integer tenantId) {
    List<EventConfig> eventConfigList = new ArrayList<>();
    
    eventConfigList.addAll(getMeeshoEventConfigList(tenantId));      // ~30 events
    eventConfigList.addAll(getPhonePeEventConfigList(tenantId));     // ~25 events
    eventConfigList.addAll(getBharatPeEventConfigList(tenantId));    // ~25 events
    eventConfigList.addAll(getGpayTermLoanEventConfigList(tenantId)); // ~40 events
    eventConfigList.addAll(getPayuEventConfigList(tenantId));         // ~35 events
    // ... more partners
    
    return eventConfigList;  // Total: 200+ events across all partners
}
```

### 3.3 Partner-Specific Event Configuration (GPay Example)

```java
private List<EventConfig> getGpayTermLoanEventConfigList(Integer tenantId) {
    // Get channel codes from database config (e.g., "GPAYTL,GPAYTL_V2")
    String channelCodes = getConfig(tenantId, Constants.GPAY_TL_CHANNEL_CODES_CONFIG_KEY);
    
    if (StringUtils.isBlank(channelCodes)) {
        return new ArrayList<>();
    }

    List<EventConfig> eventConfigList = new ArrayList<>();

    // For each GPay channel code
    for (String channelCode : channelCodes.split(",")) {
        
        // When APPLICATION_APPROVED → Generate Phase 1 docs (ASYNC)
        eventConfigList.add(createEventConfig(
            null,                                              // from (optional)
            ApplicationStage.APPLICATION_APPROVED,             // to (trigger stage)
            true,                                              // isAsync
            null,                                              // required stages
            EventType.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION, // event to fire
            channelCode                                        // partner
        ));
        
        // When PHASE_ONE_SUCCESS → Generate Phase 2 docs (ASYNC)
        eventConfigList.add(createEventConfig(
            null, 
            ApplicationStage.PHASE_ONE_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS, 
            true, 
            null,
            EventType.PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION, 
            channelCode
        ));
        
        // When PHASE_TWO_SUCCESS → Setup LMS Client (ASYNC)
        eventConfigList.add(createEventConfig(
            null, 
            ApplicationStage.PHASE_TWO_DOCUMENTS_GENERATE_DSC_NOTIFICATION_SUCCESS, 
            true, 
            null,
            EventType.LMS_CLIENT_SETUP, 
            channelCode
        ));
        
        // When LMS_CLIENT_SETUP_COMPLETED → Multiple events fire!
        eventConfigList.add(createEventConfig(
            null, 
            ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, 
            true, 
            null,
            EventType.PARTNER_APPLICATION_APPROVED_CALLBACK,  // Notify partner
            channelCode
        ));
        eventConfigList.add(createEventConfig(
            null, 
            ApplicationStage.LMS_CLIENT_SETUP_COMPLETED, 
            true, 
            null,
            EventType.CREATE_LOAN_TL,  // Create the loan
            channelCode
        ));
        
        // ... 35+ more events for GPay
    }
    
    return eventConfigList;
}
```

### 3.4 The Main Process Method (How Events Are Picked)

```java
@Override
public void process(ApplicationStage from, ApplicationStage to, 
                    String applicationId, Integer tenantId) {
    
    // Step 1: Get application to find its channel_code (partner)
    ApplicationBean application = getApplication(applicationId, tenantId);
    
    if (Objects.isNull(application) || Objects.isNull(application.getChannel_code())) {
        logger.error("Application or Channel Code not available for {}", applicationId);
        return;
    }

    // Step 2: Initialize event config map if not done (lazy loading)
    initTriggerEventConfigList(tenantId, application);

    // Step 3: CHECK - Does this partner have events for this stage?
    String channelCode = application.getChannel_code();  // e.g., "GPAYTL"
    
    if (Objects.isNull(partnerStageEventConfigMap.get(channelCode)) ||
        CollectionUtils.isEmpty(partnerStageEventConfigMap.get(channelCode).get(to))) {
        // No events configured for this partner + stage combination
        return;
    }
    
    // Step 4: Process all events for this partner + stage
    processEvents(from, to, applicationId, tenantId, application);
}
```

### 3.5 Event Execution (Sync vs Async, Multiple Threads)

```java
private void processEvents(ApplicationStage from, ApplicationStage to, 
                          String applicationId, Integer tenantId,
                          ApplicationBean application) {
    
    ApplicationDetailsDTO applicationDetails = null;
    
    // Get ALL events configured for this partner + stage
    List<EventConfig> eventsToFire = partnerStageEventConfigMap
        .get(application.getChannel_code())  // e.g., "GPAYTL"
        .get(to);                             // e.g., LMS_CLIENT_SETUP_COMPLETED
    
    // For LMS_CLIENT_SETUP_COMPLETED, this might be:
    // [CREATE_LOAN_TL, PARTNER_CALLBACK, USER_LEVEL_KYC_UPDATION]
    
    for (EventConfig eventConfig : eventsToFire) {
        
        logger.info("Trigger event for eventType {} for application id {} current status {}", 
                    eventConfig.getEventType(), applicationId, to);
        
        // Lazy load application details (once per stage)
        if (Objects.isNull(applicationDetails)) {
            applicationDetails = prepareAndGetApplicationDetailsDTO(
                application, applicationId, tenantId
            );
            if (Objects.isNull(applicationDetails)) {
                logger.error("Error in executing trigger for status {} for {}", to, applicationId);
                return;
            }
            applicationDetails.setCurrentApplicationStatus(to);
        }
        
        // Validate if all required stages are completed
        if (validateApplicationDetails(from, applicationDetails, eventConfig)) {
            
            // Get the actual event service implementation
            IEventService eventService = eventServiceFactory.get(eventConfig.getEventType());
            
            // 🔥 ASYNC vs SYNC execution 🔥
            if (eventConfig.isAsync()) {
                // ASYNC: Fire and forget using thread pool
                final ApplicationDetailsDTO appDetailsFinal = applicationDetails;
                CompletableFuture.runAsync(
                    () -> eventService.process(appDetailsFinal), 
                    taskExecutor  // ThreadPoolTaskExecutor with configurable threads
                );
            } else {
                // SYNC: Wait for completion (blocks)
                eventService.process(applicationDetails);
            }
            
        } else {
            logger.info("Validation failed for {} current status {} skipping {}", 
                       applicationId, to, eventConfig.getEventType());
        }
    }
}
```

---

## 4. THREAD POOL CONFIGURATION

```java
@Qualifier("eventThreadPoolExecutor")
@Autowired
private TaskExecutor taskExecutor;

// In configuration:
@Bean("eventThreadPoolExecutor")
public ThreadPoolTaskExecutor eventThreadPoolExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);      // Always keep 10 threads ready
    executor.setMaxPoolSize(50);       // Can scale up to 50
    executor.setQueueCapacity(100);    // Queue 100 tasks before rejecting
    executor.setThreadNamePrefix("EventExecutor-");
    executor.initialize();
    return executor;
}
```

---

## 5. THE CASCADE EFFECT (How Events Chain Together)

```
APPLICATION_APPROVED is inserted
       │
       ▼
TriggerServiceImpl.process() is called
       │
       ▼
Lookup: partnerStageEventConfigMap["GPAYTL"][APPLICATION_APPROVED]
       │
       ▼
Found events: [PHASE_ONE_DOCUMENTS..., REGISTER_USER_FOR_UCIN, CREATE_CKYC_UPLOAD...]
       │
       ├──▶ CompletableFuture.runAsync(PHASE_ONE_DOCUMENTS..., threadPool)
       │           │
       │           ▼
       │    PhaseOneDocumentDscAndNotification.process()
       │           │
       │           ├── Generate documents
       │           ├── Apply DSC signature
       │           ├── Send notification
       │           │
       │           ▼ (on success)
       │    Calls: insertApplicationTracker(PHASE_ONE_..._SUCCESS)
       │           │
       │           ▼ (RECURSION!)
       │    TriggerServiceImpl.process() is called again!
       │           │
       │           ▼
       │    Lookup: ["GPAYTL"][PHASE_ONE_SUCCESS]
       │           │
       │           ▼
       │    Found: [PHASE_TWO_DOCUMENTS...]
       │           │
       │           └──▶ CompletableFuture.runAsync(PHASE_TWO_DOCUMENTS...)
       │                       │
       │                       ▼ (on success)
       │                ... and the cascade continues ...
       │
       ├──▶ CompletableFuture.runAsync(REGISTER_USER_FOR_UCIN, threadPool)
       │           │
       │           └──▶ Runs in parallel with above
       │
       └──▶ CompletableFuture.runAsync(CREATE_CKYC_UPLOAD_ENTRY, threadPool)
                   │
                   └──▶ Runs in parallel with above
```

---

## 6. HOW MULTIPLE PARTNERS WORK

### Each partner has its OWN event configuration:

```java
// GPay events
getGpayTermLoanEventConfigList(tenantId);   // ~40 events

// Meesho events (different flow!)
getMeeshoEventConfigList(tenantId);         // ~30 events

// PhonePe events (yet another flow!)
getPhonePeEventConfigList(tenantId);        // ~25 events
```

### Same stage, different behavior:

| Stage | GPay Events | Meesho Events | PhonePe Events |
|-------|-------------|---------------|----------------|
| `APPLICATION_APPROVED` | PHASE_ONE_DOCS, REGISTER_UCIN, CREATE_CKYC | PHASE_ONE_DOCS, REGISTER_UCIN, EMAIL_DOCS | PHASE_ONE_DOCS, REGISTER_UCIN |
| `LMS_CLIENT_SETUP_COMPLETED` | CREATE_LOAN_TL, PARTNER_CALLBACK | PARTNER_CALLBACK, PHASE_THREE_DOCS | PARTNER_CALLBACK |

**The system automatically picks the right events based on `application.getChannel_code()`.**

---

## 7. CONCURRENT REQUEST HANDLING (3 ZipCredit Instances)

### Scenario: Same application, two requests hit different instances

```
Instance 1 (EC2-A)                    Instance 2 (EC2-B)
─────────────────                    ─────────────────
Request lands                        Request lands (same appId)
       │                                    │
       ▼                                    ▼
TriggerServiceImpl.process()         TriggerServiceImpl.process()
       │                                    │
       ▼                                    ▼
DocumentGeneration event fires       DocumentGeneration event fires
       │                                    │
       ▼                                    ▼
PhaseOneDocumentService.process()    PhaseOneDocumentService.process()
       │                                    │
       ▼                                    ▼
Try to acquire Redis lock            Try to acquire Redis lock
"DOC_GEN:APP123"                     "DOC_GEN:APP123"
       │                                    │
       ▼                                    ▼
   ✅ ACQUIRED                          ❌ FAILED
       │                                    │
       ▼                                    │
Generate documents                         │
       │                                    │
       ▼                                    │
Insert SUCCESS stage                       │
       │                                    │
       ▼                                    │
Release lock                        Logs "Already processing"
                                    and returns
```

**Key: Redis distributed lock ensures only ONE instance processes at a time.**

---

## 8. INTERVIEW Q&A

### Q1: "Walk me through how a stage triggers events"

**Answer:**
> "When any service completes a step, it calls `insertApplicationTracker()`. This method:
> 1. Saves the stage to `a_application_stage_tracker` table
> 2. Calls `TriggerServiceImpl.process()`
> 3. The trigger service looks up the partner's channel code (like GPAYTL or MEESHO)
> 4. It then checks `partnerStageEventConfigMap[channelCode][stage]` to find all events
> 5. For each event, it creates a `CompletableFuture.runAsync()` to fire it in parallel
> 6. When an event completes, it calls `insertApplicationTracker()` with SUCCESS stage, which triggers the next wave of events - creating a cascade."

### Q2: "How do you handle multiple partners with different flows?"

**Answer:**
> "Each partner has its own event configuration method - like `getGpayTermLoanEventConfigList()` or `getMeeshoEventConfigList()`. All these are combined into one map during initialization. At runtime, we just look up `partnerStageEventConfigMap[channelCode]` to get that partner's specific events. So GPay might trigger CREATE_LOAN_TL on LMS_CLIENT_SETUP_COMPLETED, while Meesho triggers PHASE_THREE_DOCUMENTS instead."

### Q3: "How do you prevent duplicate processing across instances?"

**Answer:**
> "We have 4 layers:
> 1. **Redis distributed lock** - Each critical event acquires a lock like `DOC_GEN:APP123`. If another instance tries, it fails to acquire.
> 2. **Idempotency check** - Before processing, we check if SUCCESS stage already exists in `a_application_stage_tracker`.
> 3. **Smart retry** - We track sub-steps (doc generated? DSC applied?), so retries skip completed work.
> 4. **Database constraint** - Unique constraint as final safeguard."

### Q4: "Why async with CompletableFuture instead of Kafka?"

**Answer:**
> "We considered Kafka but:
> - We don't need millions of events per second (our scale is ~1000 apps/day)
> - `CompletableFuture` + `ThreadPoolTaskExecutor` is simpler and runs in-process
> - No additional infrastructure to maintain
> - Easier debugging - everything is in the same JVM, we can trace logs by applicationId
> 
> We use a configurable thread pool with 10 core threads scaling to 50, so we get parallelism without Kafka's overhead."

---

## 📊 QUICK REFERENCE CARD

```
╔════════════════════════════════════════════════════════════════════════════╗
║                     STATE MACHINE CHEAT SHEET                               ║
╠════════════════════════════════════════════════════════════════════════════╣
║                                                                             ║
║  ENTRY POINT:                                                               ║
║  └── ApplicationStatusServiceImpl.insertApplicationTracker()                ║
║          │                                                                  ║
║          ├── Saves stage to a_application_stage_tracker (INSERT)           ║
║          └── Calls TriggerServiceImpl.process()                            ║
║                                                                             ║
║  EVENT LOOKUP:                                                              ║
║  └── partnerStageEventConfigMap[channelCode][stage] → List<EventConfig>    ║
║          │                                                                  ║
║          └── channelCode = "GPAYTL" | "MEESHO" | "PHONEPE" | ...           ║
║                                                                             ║
║  EVENT EXECUTION:                                                           ║
║  └── for (EventConfig event : events) {                                    ║
║          if (event.isAsync()) {                                            ║
║              CompletableFuture.runAsync(eventService.process, threadPool)  ║
║          } else {                                                          ║
║              eventService.process(appDetails);  // blocking                ║
║          }                                                                 ║
║      }                                                                     ║
║                                                                             ║
║  CASCADE EFFECT:                                                            ║
║  └── EventService.process()                                                ║
║          └── On success: insertApplicationTracker(SUCCESS_STAGE)           ║
║                  └── Triggers next wave of events (recursion!)             ║
║                                                                             ║
║  THREAD POOL:                                                               ║
║  └── eventThreadPoolExecutor                                               ║
║          ├── Core: 10 threads                                              ║
║          ├── Max: 50 threads                                               ║
║          └── Queue: 100 tasks                                              ║
║                                                                             ║
║  CONCURRENCY CONTROL:                                                       ║
║  └── 4 Layers                                                              ║
║          ├── 1. Redis Lock (Redisson)                                      ║
║          ├── 2. Idempotency Check (DB query)                               ║
║          ├── 3. Smart Retry (partial progress)                             ║
║          └── 4. DB Unique Constraint                                       ║
║                                                                             ║
╚════════════════════════════════════════════════════════════════════════════╝
```

---

**Interview Strategy:**
1. Start with: "Our state machine is event-driven with async cascading"
2. Explain the entry point: `insertApplicationTracker()` 
3. Show the lookup: `partnerStageEventConfigMap[channelCode][stage]`
4. Explain cascade: events fire → SUCCESS stage → triggers next events
5. Mention partners: "Each partner has different event configurations"
6. Explain threading: `CompletableFuture` + `ThreadPoolTaskExecutor`
7. Concurrency: Redis lock + idempotency check + smart retry + DB constraint
