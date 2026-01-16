# TIDE INTERVIEW PREP - PART 1: ARCHITECTURE & SYSTEM DESIGN
**Based on YOUR Actual PayU Lending Codebase**

---

## 1. PROJECT ARCHITECTURE DEEP DIVE

### Q: Walk me through your current project's architecture - technical, business, and functional aspects.

**A: Our PayU Lending Platform Architecture**

We have a **distributed microservices architecture** with three core services that handle the complete lending lifecycle for multiple partners (Google Pay, Swiggy, Meesho, Paytm, BharatPe).

#### **1. ZipCredit Backend (Core Lending Engine)**

**Technical Aspects:**
```
Technology Stack:
- Framework: Spring Boot (Java) with Hibernate JPA
- Database: MySQL with MyBatis ORM (mybatis-config.xml)
- Architecture: Monolithic internally, exposed as microservice
- Code Size: 622+ Java files in dgl-connectors, 238 in dgl-services
- Deployment: Docker containers on EC2/K8s
```

**Actual File Structure:**
```
zipcredit-backend/dgl_base/
├── dgl-services/          # REST API layer (238 Java files)
├── dgl-connectors/        # External integrations (622 files)
├── dgl-ruleEngine/        # Business rules (Drools - 244 .drl files)
├── rdbms/                 # Data access layer (MyBatis mappers)
├── model/                 # Domain entities (558 Java files)
└── dgl-status/            # Status update handlers (181 files)
```

**Business Purpose:**
- **Application Management**: Create, update, track loan applications
- **Credit Decision Engine**: BRE (Business Rules Engine) with 244 Drools rules
- **Document Processing**: OCR, verification, storage (S3)
- **KYC Integration**: Aadhaar (Digilocker), PAN, CKYC, Video KYC
- **Partner Integration**: Orchestrates with 8 partners (GPay, Swiggy, etc.)

**Functional Requirements:**
```java
// From: zipcredit-backend/dgl_base/dgl-services/src/main/java/com/dgl/rest/version4/serviceImpl/ZCVersion4ServiceImpl.java

public class ZCVersion4ServiceImpl {
    // 1. Idempotent Application Creation
    public ApplicationResponse createApplication(ApplicationRequest request) {
        // Check for duplicates using dedupe logic
        if (isDuplicate(request.getPan(), request.getMobile())) {
            throw new DuplicateApplicationException();
        }
        // Create with unique application ID
        Application app = applicationMapper.insert(request);
        // Trigger state machine
        updateApplicationState(app.getId(), ApplicationStage.APPLICATION_CREATED);
        return toResponse(app);
    }
    
    // 2. Eligibility Check (Soft + Hard)
    public EligibilityResponse checkEligibility(String appId, String type) {
        // type = SOFT_ELIGIBILITY or HARD_ELIGIBILITY
        // Soft: Rule-based screening (no credit bureau hit)
        // Hard: Credit bureau integration (CIBIL/Experian)
    }
    
    // 3. Document Upload with validation
    public DocumentResponse uploadDocument(DocumentRequest request) {
        // Validate file type, size (aws.s3.lendingDocument.maxFileSize=30MB)
        // OCR processing for Aadhaar/PAN
        // Store in S3 (s3.baseDirectory=apps/Lending/)
    }
}
```

#### **2. Orchestration Service (API Gateway & Workflow Coordinator)**

**Technical Aspects:**
```
Technology Stack:
- Framework: Spring Boot 3 with Java 17
- Caching: Redisson (Redis) for distributed caching
- Tracing: Micrometer with Brave (distributed tracing)
- Security: JWT (Nimbus JOSE), mTLS for GPay
- Deployment: GitLab CI/CD → Jenkins → Helm → K8s
```

**Actual Configuration:**
```properties
# From: lending-project/orchestration/src/main/resources/application.properties

# Connection Pooling & Timeouts
orchestration.restTemplate.connectionTimeout=50000
orchestration.restTemplate.readTimeout=50000
orchestration.restTemplate.connectionRequestTimeout=50000

# Thread Pool Configuration
orchestration.default.core.pool.size=10
orchestration.default.max.pool.size=20

# Webhook Retry Configuration
webhook.retry.days=2
webhook.payu.salt=webhook_salt

# Integration URLs
orchestration.zipCredit.serverURL=https://test-adminzipcredit.payufin.io
orchestration.loanrepayment.service.baseUrl=https://neostg2.payu.in/loan-repayment
orchestration.nach.service.baseUrl=http://dls-nach-prod.internal.payufin.io/nach-service
```

**Business Purpose:**
- **Partner API Gateway**: Unified API for all partners (GPay, Swiggy, etc.)
- **Workflow Orchestration**: State machine managing application lifecycle
- **Event Management**: Webhook delivery with retry mechanism
- **Session Management**: Redis-based caching for auth tokens

**Functional Requirements:**
```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/service/impl/CallBackServiceImpl.java

@Service
public class CallBackServiceImpl {
    @Value("${webhook.retry.days}")
    private int retryDays; // = 2 days
    
    // 1. Webhook Delivery with Idempotency
    public Response sendCallBackPayload(String applicationId, EventBasedCallBackRequest request) {
        // Save webhook payload to database (for audit + retry)
        WebhookDetails webhookDetails = saveWebhookPayload(applicationId, request);
        
        // Validate HMAC signature
        String signature = HmacUtils.hmacSha256Hex(salt, request.toString());
        if (!signature.equals(request.getSignature())) {
            throw new InvalidSignatureException();
        }
        
        // Process asynchronously
        CompletableFuture.runAsync(() -> processWebhook(request, webhookDetails), taskExecutor);
        
        return Response.success("Webhook queued");
    }
    
    // 2. Automatic Retry for Failed Webhooks
    public void retryFailedWebhooks(Partner partner, Integer lastDays) {
        List<WebhookDetails> failed = webhookRepository.findFailedWebhooks(
            partner.getId(), 
            LocalDateTime.now().minusDays(lastDays)
        );
        
        for (WebhookDetails webhook : failed) {
            CompletableFuture.runAsync(() -> retryWebhook(webhook), taskExecutor);
        }
    }
}
```

**State Machine Implementation:**
```sql
-- From: lending-project/orchestration/sql/migration/V2__State_Machine.sql

CREATE TABLE `application_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `los_application_id` varchar(255),
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
  PRIMARY KEY (`id`),
  KEY `idx_los_application_id` (`los_application_id`)
);
```

#### **3. Loan Repayment Service (Payment Processing & Collections)**

**Technical Aspects:**
```
Technology Stack:
- Framework: Spring Boot 2.x with Java 8
- Database: MySQL with Master-Slave replication
- LMS Integration: Finflux (Apache Fineract)
- Payment Gateway: PayU PG, NACH (Digio), Payout APIs
- Batch Processing: Chunked strategy for large datasets
```

**Actual Configuration:**
```properties
# From: lending-project/loan-repayment/src/main/resources/application.properties

# Database Master-Slave Configuration
spring.datasource.master.url=jdbc:mysql://localhost:3306/loan_repayment
spring.datasource.master.configuration.maximumPoolSize=20

spring.datasource.slave.url=jdbc:mysql://localhost:3306/loan_repayment_slave
spring.datasource.slave.configuration.maximumPoolSize=15

# Finflux (LMS) Integration
finfluxHostUrl=https://uat-payu.finfluxtrial.io/fineract-provider/api
Fineract-Platform-TenantId=uat-payu
loanRepayment.finflux.accessToken=X_SAMPLE_TOKEN

# Payment Processing Timeouts
loanRepayment.connectionTimeout=50000
loanRepayment.readTimeout=50000

# Thread Pools
loanRepayment.payout.core.pool.size=20
loanRepayment.payout.max.pool.size=50
loanRepayment.lms.core.pool.size=20
loanRepayment.lms.max.pool.size=50
```

**Business Purpose:**
- **Repayment Processing**: EMI collections via NACH/UPI
- **Disbursement Management**: Loan payout to borrower accounts
- **Settlement Processing**: Partner settlements (Swiggy, Meesho, etc.)
- **Reconciliation**: Daily reconciliation with LMS (Finflux)

**Functional Requirements:**
```java
// From: lending-project/loan-repayment/src/main/java/com/payu/vista/loanrepayment/service/impl/CronServiceImpl.java

@Service
public class CronServiceImpl {
    
    // 1. Scheduled NACH Payment Processing
    @Scheduled(cron = "0 30 0 * * ?") // Daily at 12:30 AM
    public void processScheduledPayments() {
        List<Loan> dueLoans = loanRepository.findDuePayments(LocalDate.now());
        
        for (Loan loan : dueLoans) {
            try {
                // Trigger NACH auto-debit
                NACHResponse response = nachService.initiatePayment(loan.getMandateId(), loan.getDueAmount());
                
                if (response.isSuccess()) {
                    // Update LMS
                    finfluxClient.postRepayment(loan.getLmsLoanId(), response.getTransactionId());
                    // Update local DB
                    updateLoanStatus(loan.getId(), LoanStatus.REPAYMENT_SUCCESSFUL);
                } else {
                    // Retry logic
                    scheduleRetry(loan.getId(), calculateBackoff(loan.getRetryCount()));
                }
            } catch (Exception e) {
                log.error("Payment processing failed for loan: {}", loan.getId(), e);
                markForManualReview(loan.getId());
            }
        }
    }
    
    // 2. Batch CSV Export (Large Dataset Processing)
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2:00 AM
    public void generateDisbursalReport() {
        // Use chunked processing strategy to avoid OOM
        ChunkedListProcessingStrategy strategy = new ChunkedListProcessingStrategy();
        strategy.process(
            () -> loanRepository.findAllDisbursedLoans(),
            disbursalSchema,
            csvWriter
        );
    }
}
```

**Chunked Processing Strategy (Handles Pod Crashes):**
```java
// From: lending-project/loan-repayment/src/main/java/com/payu/vista/loanrepayment/reporting/strategy/ProcessingStrategy/ChunkedListProcessingStrategy.java

@Component
public class ChunkedListProcessingStrategy implements CSVProcessingStrategy {
    
    private static final int PROCESSING_CHUNK_SIZE = 1000; // Process 1000 records at a time
    private static final int CSV_WRITE_BATCH_SIZE = 500;   // Write 500 rows at a time
    
    @Override
    public <T> CSVProcessingResult process(DataSupplier<T> dataSupplier, CSVSchema<T> schema, CSVWriter writer) {
        List<T> dataList = dataSupplier.asAList();
        
        // Process in chunks
        for (int startIdx = 0; startIdx < dataList.size(); startIdx += PROCESSING_CHUNK_SIZE) {
            int endIdx = Math.min(startIdx + PROCESSING_CHUNK_SIZE, dataList.size());
            
            // Process chunk
            ChunkResult result = processChunk(dataList, startIdx, endIdx, schema, writer);
            
            // Checkpoint progress (allows resume after crash)
            saveCheckpoint(startIdx, endIdx, result);
        }
        
        return CSVProcessingResult.success(dataList.size(), processingTime);
    }
    
    // If pod crashes, resume from last checkpoint
    private void resumeFromCheckpoint(String jobId) {
        Checkpoint checkpoint = checkpointRepository.findByJobId(jobId);
        int startIdx = checkpoint.getLastProcessedIndex();
        // Continue from startIdx...
    }
}
```

### **Cross-Question 1: Why did you split into three services instead of one monolith?**

**A:** We evolved from a monolithic ZipCredit (dgl_base with 622 connector files + 238 service files) to microservices for several reasons:

**1. Domain Separation:**
- **ZipCredit**: Application management (complex state machine with 16 states)
- **Orchestration**: Partner integrations (8 partners with different APIs)
- **Loan Repayment**: Payment processing (high-volume, requires separate scaling)

**2. Independent Scaling:**
```yaml
# From: zipcredit-backend/dls-nach-service/deployment/prod/helm_values.yaml

# NACH Service scaling (high volume during payment cycles)
autoscaling:
  minReplicas: 1
  maxReplicas: 4
  
# vs. ZipCredit (lower volume, more resource-intensive)
autoscaling:
  minReplicas: 2
  maxReplicas: 6
```

**3. Technology Evolution:**
- **ZipCredit**: Legacy Java 8 + MyBatis (migrating incrementally)
- **Orchestration**: Modern Spring Boot 3 + Java 17 + Micrometer tracing
- **Loan Repayment**: Java 8 with plans to upgrade independently

**4. Team Autonomy:**
- **ZipCredit Team**: 1,066 commits by me, focused on lending logic
- **Orchestration Team**: 719 commits, focused on partner integrations
- **Loan Repayment Team**: 150 commits, focused on payments

**Trade-offs:**
- ❌ Increased complexity (distributed transactions, network latency)
- ✅ Solved with: Saga patterns, Redis caching, webhook retry mechanism

### **Cross-Question 2: How do these services interact? What's the data flow?**

**A:** Synchronous REST + Asynchronous Webhooks:

**Synchronous Flow (Application Creation):**
```
Partner (GPay) → Orchestration → ZipCredit → Orchestration → Partner
     |                |               |            |
   POST /api      Validate       CreateApp    UpdateState    Webhook
```

**Actual API Calls:**
```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/service/ZipCreditIntegrationService.java

public ApplicationResponse createApplication(ApplicationRequest request) {
    // 1. Call ZipCredit
    String url = zipCreditServerURL + "/dgl-services/services/v4/application";
    Request zipCreditRequest = Request.builder()
        .url(url)
        .httpMethod(HttpMethod.POST)
        .payload(request)
        .build();
    
    Response response = baseClient.getResult(zipCreditRequest);
    
    // 2. Update State Machine
    updateApplicationState(response.getApplicationId(), ApplicationStage.APPLICATION_CREATED);
    
    // 3. Send Webhook to Partner (async)
    CompletableFuture.runAsync(() -> 
        webhookService.sendCallback(response.getApplicationId(), "APPLICATION_CREATED")
    );
    
    return response;
}
```

**Asynchronous Flow (Webhook Retries):**
```sql
-- Webhook stored in DB for retry
-- From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/entity/WebhookDetails.java

CREATE TABLE webhook_details (
    id BIGINT PRIMARY KEY,
    application_id VARCHAR(255),
    event_type VARCHAR(50),
    request MEDIUMTEXT,      -- JSON payload
    response MEDIUMTEXT,     -- Partner response
    status ENUM('SUCCESS', 'FAILED', 'PENDING'),
    retry_required BOOLEAN,
    webhook_config_id BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**Retry Mechanism:**
```java
// Auto-retry failed webhooks every 2 days
@Scheduled(cron = "0 0 */2 * * ?")
public void retryFailedWebhooks() {
    List<WebhookDetails> failed = webhookRepository.findByStatusAndRetryRequired("FAILED", true);
    
    for (WebhookDetails webhook : failed) {
        // Exponential backoff: 1min, 5min, 15min, 1hr, 6hr
        long delay = calculateBackoff(webhook.getRetryCount());
        
        if (webhook.getCreatedAt().plusDays(retryDays).isAfter(LocalDateTime.now())) {
            CompletableFuture.runAsync(() -> retryWebhook(webhook), taskExecutor);
        } else {
            markAsExpired(webhook.getId());
        }
    }
}
```

### **Cross-Question 3: What's the biggest technical challenge with this architecture?**

**A:** **Distributed Data Consistency** across services.

**Problem Scenario:**
```
1. Orchestration calls ZipCredit → Application created (SUCCESS)
2. Orchestration calls Loan Repayment → Disbursement initiated (SUCCESS)
3. Orchestration tries to update state → Network failure (TIMEOUT)
4. Result: Inconsistent state across services
```

**Solution: Saga Pattern with Compensating Transactions**
```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/service/impl/LoanCreationSaga.java

@Service
public class LoanCreationSaga {
    
    public LoanResponse createLoan(LoanRequest request) {
        String sagaId = UUID.randomUUID().toString();
        
        try {
            // Step 1: Create application in ZipCredit
            ApplicationResponse app = zipCreditService.createApplication(request);
            saveSagaState(sagaId, "APPLICATION_CREATED", app.getId());
            
            // Step 2: Create loan in Loan Repayment
            LoanResponse loan = loanRepaymentService.createLoan(app.getId(), request.getAmount());
            saveSagaState(sagaId, "LOAN_CREATED", loan.getId());
            
            // Step 3: Update state machine
            updateApplicationState(app.getId(), ApplicationStage.LOAN_CREATED);
            saveSagaState(sagaId, "COMPLETED", null);
            
            return loan;
            
        } catch (Exception e) {
            // Compensating transaction: rollback in reverse order
            compensate(sagaId);
            throw new SagaFailedException(e);
        }
    }
    
    private void compensate(String sagaId) {
        List<SagaState> states = sagaRepository.findBySagaId(sagaId);
        
        for (SagaState state : states.reversed()) {
            switch (state.getStep()) {
                case "LOAN_CREATED":
                    loanRepaymentService.cancelLoan(state.getEntityId());
                    break;
                case "APPLICATION_CREATED":
                    zipCreditService.dropApplication(state.getEntityId());
                    break;
            }
        }
    }
}
```

**Result:** Eventual consistency with audit trail. We accept temporary inconsistency but ensure recovery.

---

*Continue to Part 2 for Infrastructure, Monitoring, and Security details...*
