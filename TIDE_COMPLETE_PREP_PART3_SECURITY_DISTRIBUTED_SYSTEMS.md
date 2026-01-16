# TIDE INTERVIEW PREP - PART 3: REST API SECURITY, DISTRIBUTED TRANSACTIONS & SYSTEM DESIGN
**Based on YOUR Actual PayU Lending Codebase**

---

## 4. REST API SECURITY BEST PRACTICES

### Q: How do you implement security in your REST APIs? What layers of security do you have?

**A: Multi-Layer Security Architecture**

#### **1. Authentication & Authorization**

**JWT-Based Authentication:**
```java
// From: lending-project/orchestration/pom.xml - Uses Nimbus JOSE JWT library

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain chain) {
        String token = extractToken(request);
        
        if (token != null && validateToken(token)) {
            // Extract user details from token
            Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
            
            String userId = claims.getSubject();
            List<String> roles = claims.get("roles", List.class);
            
            // Set authentication context
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userId, null, mapRoles(roles));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        chain.doFilter(request, response);
    }
    
    private boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            log.error("Invalid JWT token", e);
            return false;
        }
    }
}
```

**Role-Based Access Control:**
```java
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {
    
    @PostMapping
    @PreAuthorize("hasAnyRole('PARTNER_API', 'ADMIN')")
    public ApplicationResponse createApplication(
            @RequestBody @Valid ApplicationRequest request,
            Authentication auth) {
        
        // Additional authorization: Partner can only create for themselves
        String partnerId = ((PartnerPrincipal) auth.getPrincipal()).getPartnerId();
        
        if (!request.getPartnerId().equals(partnerId)) {
            throw new UnauthorizedException("Cannot create application for different partner");
        }
        
        return applicationService.createApplication(request);
    }
    
    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAnyRole('PARTNER_API', 'ADMIN', 'VIEWER')")
    public ApplicationResponse getApplication(
            @PathVariable String applicationId,
            Authentication auth) {
        
        ApplicationResponse app = applicationService.getApplication(applicationId);
        
        // Data masking based on role
        if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_VIEWER"))) {
            // Mask sensitive data for viewers
            app.setAadhaarNumber(maskAadhaar(app.getAadhaarNumber()));
            app.setPanNumber(maskPan(app.getPanNumber()));
        }
        
        return app;
    }
}
```

#### **2. Input Validation (Multi-Layer)**

**Layer 1: Bean Validation:**
```java
// From: zipcredit-backend/dgl_base/model/src/main/java/com/dgl/model/request/ApplicationRequest.java

public class ApplicationRequest {
    
    @NotNull(message = "Customer name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Name must contain only letters")
    private String customerName;
    
    @NotNull(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number format")
    private String mobileNumber;
    
    @NotNull(message = "PAN is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN format")
    private String panNumber;
    
    @NotNull(message = "Aadhaar is required")
    @Pattern(regexp = "^[2-9]{1}[0-9]{11}$", message = "Invalid Aadhaar format")
    private String aadhaarNumber;
    
    @NotNull @Positive
    @DecimalMin(value = "5000.00", message = "Minimum loan amount is 5000")
    @DecimalMax(value = "10000000.00", message = "Maximum loan amount is 10000000")
    private BigDecimal loanAmount;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @NotNull @Future(message = "Requested date must be in future")
    private LocalDate requestedDisbursalDate;
}
```

**Layer 2: Business Validation:**
```java
@Service
public class ApplicationValidationService {
    
    public void validateApplication(ApplicationRequest request) {
        List<String> errors = new ArrayList<>();
        
        // Age validation (from PAN/Aadhaar DOB)
        LocalDate dob = extractDobFromAadhaar(request.getAadhaarNumber());
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < 21 || age > 65) {
            errors.add("Customer age must be between 21 and 65");
        }
        
        // Duplicate check (PAN + Mobile)
        if (isDuplicateApplication(request.getPanNumber(), request.getMobileNumber())) {
            errors.add("Application already exists for this PAN and mobile");
        }
        
        // Credit policy validation
        if (!meetsCreditPolicy(request)) {
            errors.add("Does not meet credit policy requirements");
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }
    }
}
```

**Layer 3: SQL Injection Prevention:**
```java
// Using MyBatis with parameterized queries
// From: zipcredit-backend/dgl_base/rdbms/src/main/resources/mappers/ApplicationMapper.xml

<select id="findByPanAndMobile" resultType="Application">
    SELECT * FROM application 
    WHERE pan_number = #{panNumber}
    AND mobile_number = #{mobileNumber}
    AND status != 'DELETED'
</select>

<!-- NEVER do this (vulnerable to SQL injection): -->
<!-- <select id="findByPanAndMobile" resultType="Application">
    SELECT * FROM application 
    WHERE pan_number = '${panNumber}'  <!-- BAD: String interpolation -->
</select> -->
```

#### **3. Data Protection & Encryption**

**Field-Level Encryption:**
```java
@Entity
@Table(name = "customer")
public class Customer {
    
    @Id
    private String id;
    
    @Column(name = "name")
    private String name;
    
    // Encrypted fields (using custom converter)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pan_number")
    private String panNumber;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "aadhaar_number")
    private String aadhaarNumber;
    
    // Fields masked in JSON responses
    @JsonIgnore
    @Column(name = "internal_notes")
    private String internalNotes;
    
    @JsonView(CustomerView.Admin.class)  // Only visible to admins
    @Column(name = "credit_score")
    private Integer creditScore;
}

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}
```

**Data Masking in Logs:**
```java
@Component
public class SensitiveDataMaskingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain chain) {
        // Wrap request to mask sensitive data in logs
        ContentCachingRequestWrapper wrappedRequest = 
            new ContentCachingRequestWrapper(request);
        
        String requestBody = new String(wrappedRequest.getContentAsByteArray());
        String maskedBody = maskSensitiveData(requestBody);
        
        log.info("Incoming request: {}", maskedBody);
        
        chain.doFilter(wrappedRequest, response);
    }
    
    private String maskSensitiveData(String json) {
        return json
            .replaceAll("\"panNumber\":\"[A-Z0-9]+\"", "\"panNumber\":\"***\"")
            .replaceAll("\"aadhaarNumber\":\"[0-9]+\"", "\"aadhaarNumber\":\"***\"")
            .replaceAll("\"accountNumber\":\"[0-9]+\"", "\"accountNumber\":\"***\"");
    }
}
```

#### **4. API Security Headers**

```java
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // Security headers
            .headers()
                .contentSecurityPolicy("default-src 'self'")
                .and()
                .frameOptions().deny()
                .and()
                .xssProtection().xssProtectionEnabled(true)
                .and()
                .contentTypeOptions()  // X-Content-Type-Options: nosniff
                .and()
                .httpStrictTransportSecurity()
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
            .and()
            
            // CORS configuration
            .cors().configurationSource(corsConfigurationSource())
            .and()
            
            // CSRF protection (disabled for REST APIs, use tokens instead)
            .csrf().disable()
            
            // Authentication
            .authorizeRequests()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            .and()
            
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("https://trusted-domain.com"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

#### **5. Rate Limiting**

```java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Value("${api.rateLimit.default:100}")
    private int defaultLimit;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain chain) {
        String clientId = extractClientId(request);
        String rateLimitKey = "rate_limit:" + clientId + ":" + LocalDateTime.now().withSecond(0);
        
        RAtomicLong counter = redissonClient.getAtomicLong(rateLimitKey);
        counter.expire(60, TimeUnit.SECONDS);
        
        long requests = counter.incrementAndGet();
        
        if (requests > defaultLimit) {
            response.setStatus(429);  // Too Many Requests
            response.setHeader("X-RateLimit-Limit", String.valueOf(defaultLimit));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(LocalDateTime.now().plusMinutes(1)));
            
            response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
            return;
        }
        
        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(defaultLimit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(defaultLimit - requests));
        
        chain.doFilter(request, response);
    }
}
```

#### **6. API Versioning Strategy**

```java
// URL-based versioning
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationControllerV1 {
    // Version 1 implementation
}

@RestController
@RequestMapping("/api/v2/applications")
public class ApplicationControllerV2 {
    // Version 2 with backward compatibility
    
    @PostMapping
    public ApplicationResponse createApplication(@RequestBody ApplicationRequestV2 request) {
        // Handle both v1 and v2 request formats
        if (request.isV1Format()) {
            return processV1Request(request);
        }
        return processV2Request(request);
    }
}
```

#### **7. HMAC Signature Validation (Webhook Security)**

```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/controller/NachCallBackController.java

@RestController
@RequestMapping("/orchestration/api/v1/nach")
public class NachCallBackController {
    
    @Value("${webhook.digio.checksumKey}")
    private String checksumKey;
    
    @PostMapping("/callback")
    public Response consumeCallback(@RequestBody DigiLockerCallback callbackRequest, 
                                   @RequestHeader("X-Digio-Signature") String signature) {
        
        // Step 1: Validate HMAC signature
        String computedSignature = HmacUtils.hmacSha256Hex(checksumKey, callbackRequest.toString());
        
        if (!MessageDigest.isEqual(signature.getBytes(), computedSignature.getBytes())) {
            log.error("Invalid signature. Expected: {}, Got: {}", computedSignature, signature);
            throw new InvalidSignatureException("Webhook signature mismatch");
        }
        
        // Step 2: Idempotency check (prevent duplicate processing)
        if (webhookRepository.existsByRequestIdAndStatus(callbackRequest.getRequestId(), "SUCCESS")) {
            log.warn("Webhook already processed: {}", callbackRequest.getRequestId());
            return Response.success("Already processed");
        }
        
        // Step 3: Process webhook
        CompletableFuture.runAsync(() -> processWebhook(callbackRequest), taskExecutor);
        
        return Response.success("Webhook accepted");
    }
}
```

### **Cross-Question 1: How do you prevent replay attacks?**

**A:** **Multi-Layer Replay Protection**

**1. Timestamp Validation:**
```java
@Component
public class ReplayAttackFilter extends OncePerRequestFilter {
    
    private static final long MAX_REQUEST_AGE_SECONDS = 300; // 5 minutes
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain chain) {
        String timestamp = request.getHeader("X-Request-Timestamp");
        
        if (timestamp == null) {
            response.setStatus(400);
            response.getWriter().write("{\"error\": \"Missing timestamp\"}");
            return;
        }
        
        long requestTime = Long.parseLong(timestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        
        if (Math.abs(currentTime - requestTime) > MAX_REQUEST_AGE_SECONDS) {
            response.setStatus(400);
            response.getWriter().write("{\"error\": \"Request too old\"}");
            return;
        }
        
        chain.doFilter(request, response);
    }
}
```

**2. Nonce (Number Used Once):**
```java
@Component
public class NonceValidationFilter extends OncePerRequestFilter {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain chain) {
        String nonce = request.getHeader("X-Request-Nonce");
        
        if (nonce == null || nonce.isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("{\"error\": \"Missing nonce\"}");
            return;
        }
        
        // Check if nonce already used (stored in Redis with TTL)
        RBucket<String> bucket = redissonClient.getBucket("nonce:" + nonce);
        
        if (bucket.isExists()) {
            response.setStatus(400);
            response.getWriter().write("{\"error\": \"Duplicate request (nonce reused)\"}");
            return;
        }
        
        // Store nonce for 5 minutes
        bucket.set("used", 5, TimeUnit.MINUTES);
        
        chain.doFilter(request, response);
    }
}
```

**3. Idempotency Key:**
```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/request/DedupeRequest.java

public class DedupeRequest {
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
    
    @JsonProperty("request_id")
    private String requestId;
}

@Service
public class IdempotencyService {
    
    public <T> T handleIdempotentRequest(String idempotencyKey, Supplier<T> operation) {
        // Check if request already processed
        IdempotencyRecord record = idempotencyRepository.findByKey(idempotencyKey);
        
        if (record != null) {
            if (record.getStatus() == Status.COMPLETED) {
                // Return cached response
                return (T) record.getResponsePayload();
            } else if (record.getStatus() == Status.PROCESSING) {
                throw new ConcurrentRequestException("Request already in progress");
            }
        }
        
        // Create idempotency record
        record = idempotencyRepository.save(IdempotencyRecord.builder()
            .key(idempotencyKey)
            .status(Status.PROCESSING)
            .createdAt(LocalDateTime.now())
            .build());
        
        try {
            // Execute operation
            T result = operation.get();
            
            // Save result
            record.setStatus(Status.COMPLETED);
            record.setResponsePayload(result);
            record.setCompletedAt(LocalDateTime.now());
            idempotencyRepository.save(record);
            
            return result;
            
        } catch (Exception e) {
            record.setStatus(Status.FAILED);
            record.setErrorMessage(e.getMessage());
            idempotencyRepository.save(record);
            throw e;
        }
    }
}
```

---

## 5. DISTRIBUTED TRANSACTIONS

### Q: How do you handle distributed transactions? When do you use 2PC vs Saga? What's your pattern?

**A: Saga Pattern with Orchestration**

#### **Why Not 2PC (Two-Phase Commit)?**

**Problems with 2PC:**
- ❌ **Blocking**: All services wait for coordinator's decision
- ❌ **Single Point of Failure**: Coordinator failure leaves transactions hanging
- ❌ **High Latency**: Multiple round trips (prepare → vote → commit)
- ❌ **Doesn't Work Across HTTP**: Requires XA protocol (not RESTful)

**When 2PC Might Work:**
- ✅ Same database, different schemas (JDBC XA transactions)
- ✅ Short-lived transactions (<1 second)
- ✅ Strong consistency absolutely required (rare in microservices)

**In Our System:** We use **Saga Pattern** because:
- Services communicate via REST APIs (not XA)
- Eventual consistency is acceptable
- Need high availability (no blocking)

#### **Saga Pattern: Orchestration (What We Use)**

**Loan Creation Saga:**
```java
// From: lending-project/orchestration/src/main/java/com/payu/vista/orchestration/service/impl/LoanCreationOrchestrator.java

@Service
public class LoanCreationOrchestrator {
    
    @Transactional
    public LoanResponse createLoan(LoanRequest request) {
        String sagaId = UUID.randomUUID().toString();
        SagaState sagaState = initializeSaga(sagaId, request);
        
        try {
            // Step 1: Create application in ZipCredit
            ApplicationResponse app = executeStep(sagaId, "CREATE_APPLICATION", () -> 
                zipCreditService.createApplication(ApplicationRequest.from(request))
            );
            
            // Step 2: Check eligibility
            EligibilityResponse eligibility = executeStep(sagaId, "CHECK_ELIGIBILITY", () ->
                zipCreditService.checkEligibility(app.getApplicationId())
            );
            
            if (!eligibility.isEligible()) {
                compensate(sagaId);
                throw new IneligibleException("Customer not eligible");
            }
            
            // Step 3: Create loan in LMS (Finflux)
            LoanResponse loan = executeStep(sagaId, "CREATE_LOAN", () ->
                loanRepaymentService.createLoan(app.getApplicationId(), request.getAmount())
            );
            
            // Step 4: Register NACH mandate
            NACHResponse nach = executeStep(sagaId, "REGISTER_NACH", () ->
                nachService.createMandate(loan.getLoanId(), request.getAccountDetails())
            );
            
            // Step 5: Update state machine
            executeStep(sagaId, "UPDATE_STATE", () ->
                updateApplicationState(app.getApplicationId(), ApplicationStage.LOAN_CREATED)
            );
            
            // Step 6: Send webhook to partner
            executeStep(sagaId, "SEND_WEBHOOK", () ->
                webhookService.sendCallback(app.getApplicationId(), "LOAN_CREATED", loan)
            );
            
            // Mark saga as completed
            completeSaga(sagaId);
            
            return loan;
            
        } catch (Exception e) {
            log.error("Saga failed: {}", sagaId, e);
            compensate(sagaId);
            throw new SagaFailedException("Loan creation failed", e);
        }
    }
    
    private <T> T executeStep(String sagaId, String stepName, Supplier<T> operation) {
        log.info("Executing saga step: {} - {}", sagaId, stepName);
        
        // Save step start
        sagaStateRepository.save(SagaStep.builder()
            .sagaId(sagaId)
            .stepName(stepName)
            .status(StepStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now())
            .build());
        
        try {
            T result = operation.get();
            
            // Save step success
            sagaStateRepository.save(SagaStep.builder()
                .sagaId(sagaId)
                .stepName(stepName)
                .status(StepStatus.COMPLETED)
                .completedAt(LocalDateTime.now())
                .result(result)
                .build());
            
            return result;
            
        } catch (Exception e) {
            // Save step failure
            sagaStateRepository.save(SagaStep.builder()
                .sagaId(sagaId)
                .stepName(stepName)
                .status(StepStatus.FAILED)
                .failedAt(LocalDateTime.now())
                .error(e.getMessage())
                .build());
            
            throw e;
        }
    }
    
    private void compensate(String sagaId) {
        List<SagaStep> completedSteps = sagaStateRepository.findBySagaIdAndStatus(
            sagaId, 
            StepStatus.COMPLETED
        );
        
        // Compensate in reverse order
        Collections.reverse(completedSteps);
        
        for (SagaStep step : completedSteps) {
            try {
                log.info("Compensating step: {} - {}", sagaId, step.getStepName());
                
                switch (step.getStepName()) {
                    case "SEND_WEBHOOK":
                        // Send cancellation webhook
                        webhookService.sendCallback(step.getApplicationId(), "LOAN_CREATION_FAILED", null);
                        break;
                        
                    case "UPDATE_STATE":
                        // Revert state
                        updateApplicationState(step.getApplicationId(), ApplicationStage.LOAN_CREATION_FAILED);
                        break;
                        
                    case "REGISTER_NACH":
                        // Cancel NACH mandate
                        nachService.cancelMandate(step.getMandateId());
                        break;
                        
                    case "CREATE_LOAN":
                        // Cancel loan in LMS
                        loanRepaymentService.cancelLoan(step.getLoanId());
                        break;
                        
                    case "CREATE_APPLICATION":
                        // Drop application (soft delete)
                        zipCreditService.dropApplication(step.getApplicationId());
                        break;
                }
                
                sagaStateRepository.save(SagaStep.builder()
                    .sagaId(sagaId)
                    .stepName(step.getStepName() + "_COMPENSATED")
                    .status(StepStatus.COMPENSATED)
                    .compensatedAt(LocalDateTime.now())
                    .build());
                
            } catch (Exception e) {
                log.error("Compensation failed for step: {} - {}", sagaId, step.getStepName(), e);
                // Continue with other compensations
            }
        }
    }
}
```

**Saga State Table:**
```sql
CREATE TABLE saga_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    saga_id VARCHAR(255) UNIQUE NOT NULL,
    saga_type VARCHAR(50) NOT NULL,  -- LOAN_CREATION, LOAN_DISBURSAL, etc.
    status ENUM('IN_PROGRESS', 'COMPLETED', 'FAILED', 'COMPENSATED'),
    request_payload TEXT,
    response_payload TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_saga_id (saga_id),
    INDEX idx_status (status)
);

CREATE TABLE saga_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    saga_id VARCHAR(255) NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    step_order INT NOT NULL,
    status ENUM('IN_PROGRESS', 'COMPLETED', 'FAILED', 'COMPENSATED'),
    started_at TIMESTAMP,
    completed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    compensated_at TIMESTAMP NULL,
    result_data TEXT,
    error_message TEXT,
    FOREIGN KEY (saga_id) REFERENCES saga_state(saga_id),
    INDEX idx_saga_id (saga_id)
);
```

### **Cross-Question 1: What if compensation also fails?**

**A:** **Dead Letter Queue + Manual Intervention**

```java
@Service
public class CompensationErrorHandler {
    
    @Autowired
    private KafkaTemplate<String, CompensationEvent> kafkaTemplate;
    
    public void handleCompensationFailure(SagaStep step, Exception error) {
        // Send to dead letter queue
        CompensationEvent event = CompensationEvent.builder()
            .sagaId(step.getSagaId())
            .stepName(step.getStepName())
            .error(error.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("compensation-dlq", event);
        
        // Send alert to operations team
        slackNotificationService.send(
            "#production-alerts",
            ":rotating_light: Compensation failed for saga: " + step.getSagaId() + 
            ". Manual intervention required."
        );
        
        // Create manual review task
        manualReviewRepository.save(ManualReview.builder()
            .type("COMPENSATION_FAILURE")
            .sagaId(step.getSagaId())
            .stepName(step.getStepName())
            .status("PENDING")
            .assignedTo("operations")
            .build());
    }
}
```

### **Cross-Question 2: Saga Orchestration vs Choreography - when to use which?**

**A:**

| Aspect | Orchestration (What We Use) | Choreography |
|--------|----------------------------|---------------|
| **Control** | Central coordinator | Distributed |
| **Visibility** | Easy to track progress | Hard to debug |
| **Complexity** | Simple workflows | Complex workflows |
| **Coupling** | Services coupled to orchestrator | Services loosely coupled |
| **Use Case** | Linear workflows (loan creation) | Event-driven (payment notifications) |

**When We Use Orchestration:**
```
Loan Creation Flow (orchestrator controls):
1. Create application → 2. Check eligibility → 3. Create loan → 4. Register NACH → 5. Send webhook
```

**When We Use Choreography:**
```
Payment Notification Flow (event-driven):
1. Payment received event →
   - Email service sends confirmation
   - SMS service sends receipt
   - Analytics service updates dashboard
   - Accounting service creates ledger entry
   (All services listen to same event, react independently)
```

---

*Continue to Part 4 for System Design Scenarios and Code Review practices...*
