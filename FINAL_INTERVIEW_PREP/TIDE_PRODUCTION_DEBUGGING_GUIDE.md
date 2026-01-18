# 🔥 PRODUCTION DEBUGGING & LOG MANAGEMENT - COMPLETE GUIDE

**For Tide Interview - How I Actually Solve Production Issues at PayU SMB Lending**

---

## TABLE OF CONTENTS

1. [Our Observability Stack](#1-our-observability-stack)
2. [How Logs Are Structured](#2-how-logs-are-structured)
3. [Step-by-Step Production Debugging Workflow](#3-step-by-step-production-debugging-workflow)
4. [Real Examples from My Experience](#4-real-examples-from-my-experience)
5. [Interview Q&A](#5-interview-qa)

---

## 1. OUR OBSERVABILITY STACK

### **Layered Observability Architecture**

```
┌─────────────────────────────────────────────────────────────────────┐
│                        MONITORING LAYERS                            │
├─────────────────────────────────────────────────────────────────────┤
│  ALERTING           │  Sentry → PagerDuty/Slack                     │
│                     │  (Exceptions trigger alerts)                  │
├─────────────────────────────────────────────────────────────────────┤
│  ERROR TRACKING     │  Sentry                                       │
│                     │  (Captures exceptions with stack traces)      │
├─────────────────────────────────────────────────────────────────────┤
│  LOG AGGREGATION    │  Coralogix                                    │
│                     │  (Centralized search across all services)     │
├─────────────────────────────────────────────────────────────────────┤
│  METRICS            │  Micrometer + Actuator                        │
│                     │  (JVM, request latency, custom metrics)       │
├─────────────────────────────────────────────────────────────────────┤
│  DATABASE QUERIES   │  Redash                                       │
│                     │  (Production DB queries - read-only)          │
├─────────────────────────────────────────────────────────────────────┤
│  RAW LOGS           │  SSH to Log Servers                           │
│                     │  (For deep investigation)                     │
└─────────────────────────────────────────────────────────────────────┘
```

### **Tool-by-Tool Breakdown**

| Tool | Purpose | When I Use It |
|------|---------|---------------|
| **Sentry** | Exception tracking, alerts | First look - see if exception is known |
| **Coralogix** | Centralized log search | Search by application_id across services |
| **Redash** | Database queries | Check application state, config, history |
| **SSH Logs** | Raw log files | Deep investigation, full context |
| **Micrometer/Actuator** | Metrics, health | Check service health, memory, threads |

---

## 2. HOW LOGS ARE STRUCTURED

### **MDC (Mapped Diagnostic Context) - The Key to Tracing**

Every request that enters our system gets enriched with context using MDC:

```java
// LogFilter.java - Entry point for every request
@Component
public class LogFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // Generate unique request ID (or use incoming one)
        String guid = UUID.randomUUID().toString();
        guid = (null != httpRequest.getHeader("oguid")) ? httpRequest.getHeader("oguid") : guid;
        
        // Set MDC context - THIS IS THE MAGIC
        MDC.put("guid", guid);                                      // Unique request ID
        MDC.put("application-id", httpRequest.getHeader("application-id"));  // Business ID
        MDC.put("hostName", InetAddress.getLocalHost().getHostName());       // Which pod
        MDC.put("service", serviceName);                            // orchestration/zipcredit
        MDC.put("clientIPAddresses", clientIPs);                    // Client IP
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();  // CRITICAL: Prevent memory leaks
        }
    }
}
```

### **Log Pattern - What Each Log Line Contains**

**Orchestration Service (logback-spring.xml):**
```xml
<Pattern>
  %d                               <!-- Timestamp: 2025-01-18 14:30:45 -->
  %p %-5level                      <!-- Log level: INFO, ERROR, WARN -->
  %C{1.}.%L                        <!-- Class.LineNumber -->
  trace_id:%X{trace_id:-unknown}   <!-- Distributed trace ID -->
  span_id:%X{span_id:-unknown}     <!-- Span for this operation -->
  requestId:%X{guid}               <!-- Unique request ID -->
  application-id:%X{application-id:-unknown}  <!-- THE KEY FOR SEARCHING -->
  host:%X{hostName}                <!-- Which pod handled this -->
  service:%X{service}              <!-- Service name -->
  [%t]                             <!-- Thread name -->
  %m%n                             <!-- The actual message -->
</Pattern>
```

**Example Log Line:**
```
2025-01-18 14:30:45.123 INFO  CallBackServiceImpl.156 
  trace_id:abc123 span_id:def456 requestId:req-789 
  application-id:APP-2024-001234 host:orchestration-pod-1 
  service:orchestration [http-nio-8080-exec-5] 
  Processing webhook for applicationId: APP-2024-001234
```

### **ZipCredit Service (logback.xml):**
```xml
<Pattern>
  %d{yyyy-MM-dd HH:mm:ss} 
  [%thread] %-5level %logger{36} 
  [traceId:%X{trace_id:-unknown}] 
  [spanId:%X{span_id:-unknown}] 
  - %msg%n
</Pattern>
```

### **Why This Matters for Debugging**

```
SCENARIO: User says "My loan creation failed for application APP-2024-001234"

SEARCH IN CORALOGIX:
application-id:APP-2024-001234 AND level:ERROR

RESULT: Find all errors across ALL services for this specific application
```

---

## 3. STEP-BY-STEP PRODUCTION DEBUGGING WORKFLOW

### **The Actual Process I Follow**

```
┌─────────────────────────────────────────────────────────────────────┐
│         PRODUCTION ISSUE INVESTIGATION FLOWCHART                    │
└─────────────────────────────────────────────────────────────────────┘

    User/Support Reports Issue
              │
              ▼
    ┌─────────────────┐
    │ 1. GET CONTEXT  │
    │ - application_id │
    │ - timestamp      │
    │ - error message  │
    └────────┬────────┘
              │
              ▼
    ┌─────────────────┐
    │ 2. CHECK SENTRY │────▶ Known exception? ──▶ Quick fix path
    │ (First look)    │
    └────────┬────────┘
              │ No known exception
              ▼
    ┌─────────────────┐
    │ 3. CORALOGIX    │
    │ Search by       │
    │ application_id  │
    │ + time range    │
    └────────┬────────┘
              │
              ▼
    ┌─────────────────┐
    │ 4. REDASH       │────▶ Check DB state:
    │ Query DB state  │      - a_application_stage_tracker
    │                 │      - a_application_details
    └────────┬────────┘      - webhook_details
              │
              ▼
    ┌─────────────────┐
    │ 5. SSH TO LOGS  │────▶ Deep dive if needed
    │ (if needed)     │      - grep for full context
    └────────┬────────┘      - check adjacent operations
              │
              ▼
    ┌─────────────────┐
    │ 6. ROOT CAUSE   │
    │ ANALYSIS        │
    └────────┬────────┘
              │
              ▼
    ┌─────────────────┐
    │ 7. FIX & VERIFY │
    └─────────────────┘
```

### **Step 1: Gather Initial Context**

```markdown
From Support/User:
- Application ID: APP-2024-001234
- Partner: GPay
- Issue: "Loan not created"
- Timestamp: ~14:30 IST today
```

### **Step 2: Check Sentry**

```
WHY SENTRY FIRST?
- If exception occurred, it's already captured
- Shows stack trace immediately
- Links to related issues (is this a pattern?)

WHAT I LOOK FOR:
- Exception type
- Stack trace
- How many times this occurred
- Which service/pod
```

### **Step 3: Search Coralogix**

```
QUERY PATTERN:
application-id:APP-2024-001234 AND (level:ERROR OR level:WARN)

THEN REFINE:
application-id:APP-2024-001234 AND "CREATE_LOAN"

CROSS-SERVICE SEARCH:
application-id:APP-2024-001234 service:orchestration
application-id:APP-2024-001234 service:zipcredit
```

### **Step 4: Query Database via Redash**

**Check Application State:**
```sql
-- What stage is the application at?
SELECT 
    application_id,
    current_status,
    previous_status,
    is_active,
    created_date_time,
    updated_date_time
FROM a_application_stage_tracker
WHERE application_id = 'APP-2024-001234'
ORDER BY created_date_time DESC;
```

**Expected vs Actual:**
```
EXPECTED FLOW:
APPLICATION_APPROVED → PHASE_ONE_DOC_SUCCESS → PHASE_TWO_DOC_SUCCESS → 
LMS_CLIENT_SETUP_COMPLETED → CREATE_LOAN_TL_SUCCESS

IF STUCK AT:
LMS_CLIENT_SETUP_COMPLETED (CREATE_LOAN_TL not triggered)
→ Problem is in loan creation trigger
```

**Check Webhook Status:**
```sql
-- Did webhook succeed?
SELECT 
    id,
    application_id,
    event_type,
    status,
    response,
    retry_required,
    created_at
FROM webhook_details
WHERE application_id = 'APP-2024-001234'
ORDER BY created_at DESC;
```

### **Step 5: SSH to Log Servers (Deep Dive)**

```bash
# Connect to production log server
ssh -A 10.165.10.29 -p 33000 "ssh 10.165.10.190 -p 33000"

# Search across all services
for service in orchestration zipcredit loanrepayment; do
  echo "=== $service ==="
  grep -n "APP-2024-001234" /logs/$service/$service-$(date +%Y-%m-%d).log | \
    grep -i "error\|exception\|failed" | tail -20
done

# Get full context around error
grep -B5 -A10 "APP-2024-001234.*ERROR" /logs/zipcredit/zipcredit-2025-01-18.log
```

### **Step 6: Root Cause Analysis Template**

```markdown
## Issue Analysis: APP-2024-001234

### Timeline:
- 14:30:15 - Application approved (Orchestration)
- 14:30:16 - Phase 1 documents generated (ZipCredit)
- 14:30:18 - Phase 2 documents generated (ZipCredit)
- 14:30:20 - LMS client setup started
- 14:30:45 - ERROR: LMS API timeout (root cause)
- 14:31:00 - Retry attempted
- 14:31:30 - Retry succeeded
- 14:31:32 - CREATE_LOAN_TL triggered

### Root Cause:
LMS external API had temporary latency spike (45s timeout)

### Resolution:
Automatic retry mechanism handled it. No manual intervention needed.

### Prevention:
- Alert threshold set: If LMS latency > 10s, trigger warning
- Circuit breaker configured: 5 failures → open circuit
```

---

## 4. REAL EXAMPLES FROM MY EXPERIENCE

### **Example 1: GPay Validation Failures (Cache Race Condition)**

**How I identified it:**

```
STEP 1: Sentry Alert
- 50+ "Application not approved" errors in 1 hour
- All from GPay partner
- Intermittent - same application succeeds on retry

STEP 2: Coralogix Analysis
- Searched: application-id:APP-XYZ AND "not approved"
- Found: Application WAS approved (APPROVED stage exists)
- But validation was reading STALE cache

STEP 3: Code Analysis
- Found validateApplicationStatus() was using cached data
- Cache TTL was 5 minutes
- But approval → loan creation happened in <1 second
- Result: Cache returned OLD state (not approved)

STEP 4: Fix
- Bypass cache for critical validations
- Add retry with exponential backoff
- Direct DB read via selectApplicationTrackerFromDB()
```

**The Actual Fix (from my commit 31ed9d129f):**
```java
private void validateApplicationStatus(String applicationId, Integer tenantId, boolean termLoan) {
    int maxRetries = 3;
    int retryDelayMs = 100;
    
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        // BYPASS CACHE - read directly from database
        applicationTrackerBeanList = applicationTrackerService.selectApplicationTrackerFromDB(applicationId, tenantId);
        
        // Validate status...
        
        if (validationPassed) {
            logger.info("Validation passed on attempt {}", attempt);
            return;
        }
        
        // Exponential backoff before retry
        Thread.sleep(retryDelayMs);
        retryDelayMs *= 2;  // 100ms → 200ms → 400ms
    }
    
    throw new ZcV4Exception("Application validation failed after " + maxRetries + " attempts");
}
```

### **Example 2: Memory Leak in Orchestration**

**How I identified it:**

```
STEP 1: PagerDuty Alert (3 AM)
- "orchestration-pod-1 memory 90%"
- Service running for 2 days without restart

STEP 2: Initial Analysis
- Checked Micrometer metrics: memory steadily increasing
- Not correlated with traffic volume
- Suspected: Object not being garbage collected

STEP 3: Heap Dump Analysis
- Generated heap dump via actuator
- Found: Google Pay context objects accumulating
- GooglePayContextHolder not clearing ThreadLocal

STEP 4: Root Cause
- ThreadLocal not cleared in finally block
- When async processing happened, thread was returned to pool
- But context remained attached → memory leak

STEP 5: Fix
```java
// LogFilter.java - Added proper cleanup
try {
    filterChain.doFilter(servletRequest, servletResponse);
} finally {
    // CRITICAL: Always clear to prevent memory leaks
    MDC.clear();
    GooglePayContextHolder.clearGooglePaySubject();  // This was missing!
}
```

---

## 5. INTERVIEW Q&A

### **Q1: "How do you solve production issues?"**

**Answer:**
> "I follow a structured 6-step approach:
> 
> **Step 1: Context Gathering** - Get application_id, timestamp, error message from support/user
> 
> **Step 2: Sentry Check** - See if exception is already captured, check stack trace
> 
> **Step 3: Coralogix Search** - Search logs across all services using application_id as correlation key
> 
> **Step 4: Database State** - Query Redash to check application state in `a_application_stage_tracker`
> 
> **Step 5: Deep Dive** - If needed, SSH to log servers for full context
> 
> **Step 6: Root Cause & Fix** - Document timeline, identify root cause, implement fix
> 
> The key is our MDC-based logging. Every log line contains `application-id`, which acts as a correlation ID across all services. This lets me trace a single application's journey through orchestration → zipcredit → loan-repayment."

---

### **Q2: "How do you manage logs across multiple services?"**

**Answer:**
> "We use a combination of:
> 
> **1. Structured Logging with MDC:**
> - Every request gets unique `guid` (request ID)
> - Business context via `application-id` header
> - Host/pod information for identifying which instance
> 
> **2. Coralogix for Centralized Search:**
> - All logs shipped to Coralogix
> - Can search across services in one query
> - Example: `application-id:APP-123 AND level:ERROR`
> 
> **3. Consistent Log Pattern:**
> ```
> timestamp | level | class | trace_id | span_id | requestId | application-id | host | service | message
> ```
> 
> **4. Sentry for Exceptions:**
> - Automatic capture of all exceptions
> - Stack traces with context
> - Linked to alerts via PagerDuty/Slack
> 
> The benefit? I can see an application's entire journey in one place, even though it passes through 3 different services and 8 different pods."

---

### **Q3: "What do you do when a critical production issue occurs at 3 AM?"**

**Answer:**
> "I've actually dealt with this - a memory leak that triggered at 3 AM.
> 
> **Immediate Actions (first 15 mins):**
> 1. Acknowledge the alert (PagerDuty)
> 2. Check service health - is it still serving traffic?
> 3. If critical, immediately scale up pods (temporary relief)
> 4. Post in incident channel (Slack)
> 
> **Investigation (next 30 mins):**
> 1. Check Sentry for recent exceptions
> 2. Look at Micrometer metrics trend
> 3. Identify pattern - is it traffic-related? Time-based?
> 
> **Resolution:**
> - If fixable quickly → deploy hotfix
> - If complex → implement temporary mitigation (restart pods, scale up)
> - Document for next day follow-up
> 
> **Post-Incident:**
> - RCA document
> - Prevention measures
> - Update alert thresholds if needed"

---

### **Q4: "How do you ensure you can debug issues quickly?"**

**Answer:**
> "Prevention is better than cure. We've set up:
> 
> **1. Correlation IDs Everywhere:**
> - Every request gets MDC context
> - `application-id` is the golden key for searching
> 
> **2. Meaningful Log Messages:**
> ```java
> // BAD
> logger.error("Error occurred");
> 
> // GOOD
> logger.error("CREATE_LOAN_TL failed for applicationId: {} tenantId: {} reason: {}", 
>              applicationId, tenantId, e.getMessage(), e);
> ```
> 
> **3. State Tracking:**
> - `a_application_stage_tracker` records every state change
> - I can see exactly where an application stopped
> 
> **4. Error Context in Sentry:**
> - We capture extra context before sending to Sentry
> - applicationId, partnerId, stage - all attached to exception
> 
> **5. Proactive Alerting:**
> - 75% memory warning (Slack)
> - 85% memory critical (PagerDuty)
> - GC pause time alerts
> - API latency thresholds"

---

### **Q5: Cross-Question: "What if Coralogix is down?"**

**Answer:**
> "We have fallbacks:
> 
> 1. **Direct SSH to pods** - Logs are stored locally on each pod
> 2. **Redash queries** - Database state tells the story even without logs
> 3. **Sentry** - Exception tracking is separate from log aggregation
> 
> Command I'd use:
> ```bash
> kubectl logs -f deployment/orchestration --all-containers | grep "APP-123"
> ```
> 
> Or SSH to the log server where logs are persisted."

---

### **Q6: Cross-Question: "How do you handle debugging when the issue is intermittent?"**

**Answer:**
> "Intermittent issues are the hardest. My approach:
> 
> **1. Statistical Analysis:**
> - Is it time-based? (peak hours)
> - Is it load-based? (after N requests)
> - Is it partner-specific? (only GPay?)
> 
> **2. Add Targeted Logging:**
> - If I suspect a specific flow, add DEBUG logs temporarily
> - Deploy → capture → analyze → remove
> 
> **3. Check for Race Conditions:**
> - Review concurrent operations
> - Look for missing locks
> - Check cache invalidation timing
> 
> **4. Real Example:**
> - GPay cache race condition was intermittent
> - Happened only when approval and loan creation were <500ms apart
> - Found by checking timestamp differences in logs"

---

## QUICK REFERENCE CARD

```
╔══════════════════════════════════════════════════════════════╗
║              PRODUCTION DEBUGGING CHEAT SHEET                ║
╠══════════════════════════════════════════════════════════════╣
║  TOOLS:                                                      ║
║  ├── Sentry: Exception tracking                              ║
║  ├── Coralogix: Centralized log search                       ║
║  ├── Redash: Database queries                                ║
║  ├── Micrometer/Actuator: Metrics & health                   ║
║  └── SSH: Deep dive raw logs                                 ║
╠══════════════════════════════════════════════════════════════╣
║  KEY LOG FIELDS:                                             ║
║  ├── application-id: Business correlation key                ║
║  ├── trace_id: Distributed trace                             ║
║  ├── guid: Request ID                                        ║
║  ├── hostName: Which pod                                     ║
║  └── service: Which service                                  ║
╠══════════════════════════════════════════════════════════════╣
║  DATABASE TABLES FOR DEBUGGING:                              ║
║  ├── a_application_stage_tracker: State history              ║
║  ├── a_application_details: Application data                 ║
║  ├── webhook_details: Callback status                        ║
║  └── a_system_config: Configuration                          ║
╠══════════════════════════════════════════════════════════════╣
║  CORALOGIX QUERIES:                                          ║
║  ├── application-id:APP-123 AND level:ERROR                  ║
║  ├── application-id:APP-123 service:orchestration            ║
║  └── "CREATE_LOAN" AND level:ERROR                           ║
╚══════════════════════════════════════════════════════════════╝
```

---

**Key Takeaways for Interview:**
1. **Correlation IDs are essential** - application-id is the golden key
2. **Structured approach** - 6-step debugging workflow
3. **Multiple tools** - Don't rely on just one (Sentry, Coralogix, Redash)
4. **Prevention** - Good logging > reactive debugging
5. **Real examples** - Cache race condition, memory leak stories
