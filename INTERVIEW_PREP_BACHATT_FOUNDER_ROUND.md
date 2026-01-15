# 🎯 Bachatt Founder Round Interview Preparation
## Shailender Kumar | Senior Software Engineer | 5+ Years Backend Experience

---

# 📋 TABLE OF CONTENTS
1. [Interview Overview & Timeline](#interview-overview--timeline)
2. [Your 60-Second Intro Story](#your-60-second-intro-story)
3. [Deep Dive: Your Projects & Contributions](#deep-dive-your-projects--contributions)
4. [Behavioral Questions & STAR Answers](#behavioral-questions--star-answers)
5. [Fintech & Lending Domain Vocabulary](#fintech--lending-domain-vocabulary)
6. [AI Tools & Modern Development](#ai-tools--modern-development)
7. [Questions to Ask the Founder](#questions-to-ask-the-founder)
8. [Quick Reference Cheat Sheet](#quick-reference-cheat-sheet)

---

# 📅 INTERVIEW OVERVIEW & TIMELINE

## Interview Details
- **Round**: Founder Round (2nd Round)
- **Duration**: 45 minutes
- **Interviewers**: 
  - [Arjav Jain](https://www.linkedin.com/in/jainarjav/) - Co-founder
  - [Mayank Agarwal](https://www.linkedin.com/in/mayankagarwal245/)
- **Company**: [Bachatt](https://www.bachatt.app/) - Daily savings app with 10L+ users

## Bachatt Company Context
From [Bachatt Website](https://www.bachatt.app/):
- **Mission**: Help Indians save ₹100 daily → ₹1.35 Lakhs in 3 years
- **Users**: 10 Lakh+ active users
- **Total Invested**: ₹50+ Crore
- **SEBI Registered**: ARN-321640
- **Partners**: SBI, ICICI, Axis, HDFC, Aditya Birla AMCs
- **Key Features**: UPI Autopay, Daily/Weekly SIP, 2-min KYC, Instant Withdrawals

## 45-Minute Interview Flow
| Time | Phase | Focus |
|------|-------|-------|
| 0-5 min | **Introduction** | Your story, why Bachatt |
| 5-20 min | **Technical Deep Dive** | 1-2 major projects |
| 20-35 min | **Product/Domain** | Fintech knowledge, system design |
| 35-45 min | **Culture Fit** | Your questions, ownership discussion |

---

# 🎤 YOUR 60-SECOND INTRO STORY

## Template
```
"I'm Shailender Kumar, a Senior Software Engineer with 5+ years of backend 
experience, specializing in Java, Spring Boot, and distributed systems. 

At PayU Lending, I've:
✅ Led end-to-end partner integrations (Google Pay, PhonePe, BharatPe, Paytm, Swiggy)
✅ Built microservices from scratch - NACH mandate service, Insurance service
✅ Improved API latency by 20% with Redis caching
✅ Designed state machines reducing partner onboarding by 30%

What excites me about Bachatt is building trusted financial infrastructure 
for daily savings - turning small habits into significant wealth. My experience 
with NACH mandates, payment flows, and compliance directly aligns with 
enabling UPI autopay and SIP automation at scale."
```

## Why Bachatt (Authentic Reasons)
1. **Domain Alignment**: Built NACH/UPI mandate systems → directly relevant to SIP autopay
2. **Scale Challenge**: 10L+ users, daily transactions → system reliability matters
3. **Consumer Trust**: Worked on KYC, compliance → understand regulatory importance
4. **Ownership Culture**: Built services end-to-end → thrive in startup environment

---

# 🔬 DEEP DIVE: YOUR PROJECTS & CONTRIBUTIONS

## Project 1: DLS NACH Service (FLAGSHIP PROJECT)
**Location**: `/zipcredit-backend/dls-nach-service/`

### Business Context
NACH (National Automated Clearing House) is the backbone of recurring payments in India. This service handles UPI mandate creation and lifecycle management for loan EMI collections.

### Architecture Overview
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Orchestration │────▶│  DLS NACH        │────▶│   Digio API     │
│   Service       │     │  Service         │     │   (Provider)    │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌──────────────────┐
                        │   ZipCredit      │
                        │   State Machine  │
                        └──────────────────┘
```

### Key Code Contributions

#### 1. Factory Pattern for Multi-NACH Type Support
```java
// /dls-nach-service/src/main/java/com/smb/nach_service/service/factory/
public class DigioCallbackServiceFactory {
    @Autowired
    private DigioUpiMandateCallbackService upiCallbackService;
    
    public DigioCallbackService getStrategy(NachTypeEnum nachType) {
        return switch (nachType) {
            case UPI -> upiCallbackService;
            case API -> apiCallbackService;
            case PHYSICAL -> physicalCallbackService;
        };
    }
}
```

#### 2. NACH Creation Flow with Validations
```java
// NachService.java - Core creation logic
public CreateNachResponseDTO createNach(CreateNachRequestDTO request) {
    // 1. Bank details validation
    nachValidationService.createNachBankDetailsValidation(bankDetails);
    nachValidationService.createNachIfscCodeValidation(bankDetails);
    nachValidationService.createNachAccountNumberValidation(bankDetails);
    
    // 2. IFSC to Digio bank mapping
    String bankId = ifscDigioMappingDBService.findFirstDigioIdByIfsc(ifscCode);
    
    // 3. Validate NACH type supported by bank
    nachValidationService.isValidNachTypeForBank(nachType, digioDataDumpDTO);
    
    // 4. Digio API integration
    DigioCreateNachReqResWrapperDTO response = digioIntegrationService.createNach(request);
    
    // 5. State machine integration
    zipCreditIntegrationService.addEntryToZCStateMachine(
        customerRefId, ZcApplicationStageEnum.UPI_MANDATE_GENERATED);
    
    return buildResponse(response);
}
```

#### 3. Secure Webhook Callback Processing
```java
// NachController.java - HMAC-SHA256 validation
@PostMapping(ApiEndpointConstants.CALLBACK)
public ResponseEntity<?> consumeCallback(
        @RequestHeader("X-Digio-Checksum") String digioChecksum,
        @RequestBody String requestString) {
    
    // HMAC-SHA256 authentication
    if (!DigioUtility.authenticateDigioCallback(digioChecksum, apiKey, requestString)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    
    nachService.consumeCallback(request, tenant, type);
    return ResponseEntity.ok().build();
}
```

#### 4. Multi-Tenant Data Backfilling
```java
// Strategy pattern for different tenant data sources
public interface INachDataBackfillService {
    void backfillCreateNachData(CreateNachRequestDTO request);
}

// Factory to get tenant-specific backfill strategy
INachDataBackfillService backfillStrategy = nachDataBackFillServiceFactory.getStrategy(tenant);
backfillStrategy.backfillCreateNachData(request);
```

### Impact Metrics (to mention)
- **Reliability**: HMAC validation prevented unauthorized callback processing
- **Flexibility**: Strategy pattern enabled easy addition of new NACH types
- **Integration**: State machine updates ensure loan workflow consistency
- **Observability**: Comprehensive logging with MDC context

---

## Project 2: InsureX Service
**Location**: `/zipcredit-backend/insure-x/`

### Business Context
Insurance policy management service for lending products - auto-debit insurance premium and generate Certificate of Insurance (COI).

### Architecture Decisions

#### 1. Factory Pattern for Multi-Vendor Support
```java
// InsuranceVendorFactory.java
@Component
public class InsuranceVendorFactory {
    @Autowired
    private IciciInsuranceVendorImpl iciciInsuranceVendor;
    @Autowired
    private AckoInsuranceVendorImpl ackoInsuranceVendor;

    public InsuranceVendor getInsuranceVendor(String vendorCode) {
        return switch (vendorCode.toUpperCase()) {
            case "ICICI" -> iciciInsuranceVendor;
            case "ACKO" -> ackoInsuranceVendor;
            default -> throw new InsureXException(ErrorCode.INVALID_VENDOR);
        };
    }
}
```

#### 2. Two-Phase API Flow (Policy + COI)
```java
// IciciInsuranceVendorImpl.java
@Override
public PolicyNumberResponse getPolicyNumber(InsurancePolicyRequest request, 
                                            Long clientInfoId, Long policyDetailsId) {
    // Phase 1: Create policy with vendor
    PolicyRequest policyRequest = insuranceVendorUtility.populatePolicyRequest(request);
    PolicyNumberResponse response = callVendorApi(policyRequest);
    
    // Update policy details
    policyDetailsService.updatePolicyWithApiResponse(response, requestId);
    return response;
}

@Override
public CertificateResponse getPolicyCertificate(PolicyCertificateRequest request, 
                                                 Long clientInfoId, Long policyDetailsId) {
    // Phase 2: Fetch COI document
    byte[] pdfDocument = fetchCertificateFromVendor(policyId);
    DocumentMetaData savedDoc = documentService.saveDocumentWithS3(policy, pdfDocument);
    return CertificateResponse.builder().DocumentContent(savedDoc.getFileSystemPath()).build();
}
```

#### 3. Cron-Based Retry System
```java
// Retry failed policy creations
@Override
public PolicyNumberResponse retryInsuranceCron(Long policyDetailsId, String requestId, 
                                                Long clientInfoId, ApiEventDetails apiEventDetails) {
    PolicyRequest policyRequest = getIciciPolicyRequest(apiEventDetails);
    return getPolicyNumberResponse(policyDetailsId, policyRequest, requestId, clientInfoId);
}
```

#### 4. Comprehensive Audit Trail
```java
private void saveApiEvent(String requestId, Long policyDetailsId, ApiEvent eventName, 
                          Object request, Object response, Boolean isSuccess) {
    String requestJson = objectMapper.writeValueAsString(request);
    String responseJson = objectMapper.writeValueAsString(response);
    Integer statusCode = isSuccess ? 200 : 500;
    
    apiEventService.saveApiEvent(requestId, policyDetailsId, eventName, 
                                  statusCode, status, requestJson, responseJson);
}
```

---

## Project 3: Redis Caching in Orchestration Service
**Location**: `/lending-project/orchestration/`

### Implementation
```java
// CustomRedisCacheManager.java
@Slf4j
public class CustomRedisCacheManager implements CacheManager {
    private final RedissonClient redissonClient;
    private final Map<String, Long> ttlConfig;

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, cacheName -> {
            if (redissonClient == null) {
                return new NoOpSpringCache(cacheName); // Graceful fallback
            }
            return new RedissonCache(redissonClient, cacheName, getTtl(cacheName));
        });
    }
}

// RedissonCache implementation
public static class RedissonCache implements Cache {
    @Override
    public void put(Object key, Object value) {
        if (value != null) {
            mapCache.fastPut(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
    }
}
```

### Auth Token Caching
```java
// RedisAuthCacheServiceImpl.java
@Override
public Optional<GetTokenInfoResponse> getTokenFromCache(String token) {
    RMapCache<String, String> tokenCache = redissonClient.getMapCache(AUTH_TOKEN_CACHE);
    String cachedValue = tokenCache.get(token);
    
    if (cachedValue == null) return Optional.empty();
    
    return Optional.of(objectMapper.readValue(cachedValue, GetTokenInfoResponse.class));
}

@Override
public void cacheToken(String token, GetTokenInfoResponse response, long ttlMinutes) {
    String jsonValue = objectMapper.writeValueAsString(response);
    tokenCache.fastPut(token, jsonValue, ttlMinutes, TimeUnit.MINUTES);
}
```

### Impact
- **20% reduction in API latency** for frequently accessed data
- **Graceful degradation** when Redis unavailable
- **TTL-based cache invalidation** prevents stale data

---

## Project 4: ConfigNexus - AI-Powered Config Management
**Location**: `/config-nexus-mcp/` + `/config-manager-dashboard/`

### What You Built
A **Model Context Protocol (MCP) server** enabling AI agents to interact with configuration management:

```python
# Available MCP Tools
| Tool | Description |
|------|-------------|
| confignexus_search_configs | Search configurations by keyword |
| confignexus_get_config | Get detailed config by category |
| confignexus_list_partners | List all partners/channels |
| confignexus_get_change_request | Get CR details |
| database_query | Execute read-only SQL queries |
```

### Dashboard Features Built
```
/config-manager-dashboard/src/pages/
├── ChangeRequests.jsx      - CR workflow management
├── PartnerConfig.jsx       - Partner configuration
├── VersionHistory.jsx      - Config versioning
├── TenantManagement.jsx    - Multi-tenant support
├── MockConfigManager.jsx   - Testing configurations
└── AdminApprovalDashboard.jsx - Approval workflow
```

### Integration with Cursor AI
```json
// ~/.cursor/mcp.json
{
  "mcpServers": {
    "confignexus-mcp": {
      "url": "https://confignexus-mcp-uat.payufin.io/mcp/v1/message"
    }
  }
}
```

---

## Project 5: Partner Integrations
**Partners Integrated**: Google Pay, PhonePe, BharatPe, Paytm, Swiggy

### Partner Enum Definition
```java
// Partner.java
public enum Partner {
    SWIGGY("SWIGGY"),
    MEESHO("MEESHO"),
    PHONEPE("PHONEPE"),
    BHARATPE("BHARATPE"),
    PAYTM("PAYTM"),
    GPAY("GPAY");
}
```

### Channel Code Patterns
| Partner | Channel Codes | Product Types |
|---------|---------------|---------------|
| **PhonePe** | `edi_pp_01` | EDI Term Loan |
| **Swiggy** | `as_swiggy_01`, `tl_swiggy_01` | Credit Line, Term Loan |
| **Meesho** | `as_meesho_01`, `tl_meesho_01` | Credit Line, Term Loan |
| **BharatPe** | `edi_bhp_01`, `as_bp_01` | EDI Products |
| **Paytm** | `edi_paytm_01` | EDI Products |
| **Google Pay** | `GPAY` | Identity-based loans |

---

# 💬 BEHAVIORAL QUESTIONS & STAR ANSWERS

## 1. "Tell me about a project you owned end-to-end"

### STAR Response: DLS NACH Service
**S**ituation: "PayU needed a dedicated microservice for UPI mandate management. The existing system was coupled with the main lending service, causing maintenance issues."

**T**ask: "I was tasked to design and build a standalone NACH service from scratch supporting UPI, API, and Physical NACH types."

**A**ctions:
- Designed microservice architecture with clear separation
- Implemented Strategy/Factory patterns for multi-NACH support
- Integrated with Digio API for mandate creation
- Built secure webhook processing with HMAC-SHA256
- Added state machine integration for loan workflow
- Created multi-tenant data backfilling mechanism

**R**esults:
- Service handles 100% of UPI mandate operations
- Strategy pattern enabled easy addition of new NACH types
- Zero unauthorized callback processing since launch
- Reduced integration time for new partners by 40%

---

## 2. "Describe a time you improved system performance"

### STAR Response: Redis Caching
**S**ituation: "Orchestration service had high latency for repeated API calls, especially auth token validation."

**T**ask: "Reduce API latency without compromising reliability."

**A**ctions:
- Analyzed API call patterns, identified cacheable operations
- Implemented CustomRedisCacheManager with Redisson
- Added NoOp cache fallback for Redis unavailability
- Configured TTL-based invalidation per cache type
- Implemented auth token caching with security considerations

**R**esults:
- **20% reduction in API latency**
- System remains operational even when Redis is down
- Reduced database load for frequent queries

---

## 3. "How do you handle disagreements with product/stakeholders?"

**Example**: "When implementing the NACH service, product wanted to skip IFSC validation to speed up the flow. I:
1. **Showed data**: Invalid IFSC codes caused 15% mandate failures
2. **Proposed compromise**: Async validation with cached IFSC mapping
3. **Aligned on goal**: Both wanted fewer failures, not just faster flow
4. **Result**: IFSC validation added, mandate success rate improved 12%"

---

## 4. "Tell me about a failure and what you learned"

**Example**: "Early in the InsureX project, I didn't account for vendor API rate limits. First batch of 500 policies caused throttling.

**Learned**:
- Always check vendor SLAs and rate limits
- Implemented exponential backoff retry
- Added cron-based retry for failed policies
- Now always ask: 'What happens at scale?'"

---

## 5. "How do you stay current with technology?"

**Response**:
- "Use AI tools (Cursor, ChatGPT) for code exploration and scaffolding
- Built ConfigNexus MCP server to integrate AI with our config management
- Read engineering blogs (Martin Fowler, Netflix Tech Blog)
- Qualified GATE 2019, continuous learning mindset"

---

# 📚 FINTECH & LENDING DOMAIN VOCABULARY

## Core Lending Concepts

### Loan Lifecycle
```
APPLICATION → KYC → UNDERWRITING → APPROVAL → DISBURSEMENT → REPAYMENT → CLOSURE
     ↓           ↓         ↓           ↓            ↓             ↓         ↓
   Created    Verified   Risk Assessed  Sanctioned   Funds Sent   EMIs Due   Paid Off
```

### Loan Status Definitions
| Status | Description | Business Logic |
|--------|-------------|----------------|
| `CREATED` | Initial application | System generated |
| `APPLIED` | Submitted to LMS | Sent to FinFlux |
| `APPROVAL_PENDING` | Under review | Risk assessment |
| `APPROVED` | Credit approved | Ready for disbursement |
| `DISBURSED` | Amount sent | Active loan |
| `REPAYMENT_SUCCESSFUL` | EMI received | On-time payment |
| `REPAYMENT_FAILED` | Payment bounced | DPD tracking starts |
| `CLOSED` | Fully repaid | Final settlement |
| `FORECLOSED` | Early closure | Pre-payment |

### Application Stages (Workflow)
```
CREATED → APPLICANT_DETAIL_UPDATED → COMPANY_DETAIL_UPDATED → 
LOAN_DETAIL_UPDATED → BANK_DETAIL_UPDATED → SOFT_ELIGIBILITY_APPROVED → 
OFFERS_GENERATED → OFFERS_ACCEPTED → FINAL_ELIGIBILITY_APPROVED → 
SANCTION_GENERATED → KFS_SIGNED → DISBURSED
```

---

## NACH (National Automated Clearing House)

### What is NACH?
- RBI's centralized clearing system for bulk/repetitive transactions
- Used for EMI auto-debit from customer's bank account
- Types: **eNACH** (electronic), **Physical NACH**, **UPI NACH**

### NACH Lifecycle
```
MANDATE_CREATED → MANDATE_SUBMITTED → MANDATE_APPROVED → ACTIVE → REVOKED/EXPIRED
```

### Key Terms
| Term | Definition |
|------|------------|
| **UMRN** | Unique Mandate Reference Number - unique ID for mandate |
| **Sponsor Bank** | Bank initiating the debit (lender's bank) |
| **Destination Bank** | Customer's bank account |
| **Mandate Amount** | Maximum amount that can be debited |
| **Frequency** | Monthly, quarterly, one-time |

### UPI Mandate (Relevant for Bachatt)
```java
// UPI Mandate for SIP/recurring payments
- UPI Autopay limit: ₹1 Lakh per transaction
- Instant creation via UPI apps
- Real-time mandate status
- Customer revokes via UPI app
```

---

## KYC (Know Your Customer)

### KYC Types
| Type | Method | Time | Use Case |
|------|--------|------|----------|
| **eKYC** | Aadhaar OTP | 2 mins | Small loans |
| **Video KYC** | Live video verification | 5 mins | RBI-compliant |
| **CKYC** | Central KYC Registry | Varies | Re-KYC |
| **Offline Aadhaar** | XML/QR download | 2 mins | Privacy-focused |

### KYC Flow
```
PAN Verification → Aadhaar Details → Aadhaar OTP → Face Match → KYC Complete
```

---

## Credit Bureau

### What is Bureau Pull?
- Fetching credit history from CIBIL/Experian/Equifax
- CIBIL Score range: 300-900
- Used for creditworthiness assessment

### Score Interpretation
| Score Range | Risk Level | Typical Action |
|-------------|------------|----------------|
| 750+ | Low Risk | Auto-approve |
| 650-749 | Medium Risk | Manual review |
| 550-649 | High Risk | Lower limits |
| <550 | Very High | Reject |

---

## Payments & Settlements

### Transfer Modes
| Mode | Full Form | Speed | Limit |
|------|-----------|-------|-------|
| **IMPS** | Immediate Payment Service | Instant | ₹5 Lakh |
| **NEFT** | National Electronic Funds Transfer | 30 mins | No limit |
| **RTGS** | Real Time Gross Settlement | Instant | ₹2 Lakh+ |
| **UPI** | Unified Payments Interface | Instant | ₹1 Lakh |

### Penny Drop
- Small amount (₹1) transfer to verify bank account
- Confirms account holder name matches
- Mandatory before disbursement

---

## Bachatt-Specific Concepts

### Daily SIP via UPI Autopay
```
User Sets Amount (₹51+) → UPI Mandate Created → 
Daily Auto-Debit → Invested in Mutual Fund → 
Compounding Returns
```

### Mutual Fund Distribution
- **SEBI Registered**: ARN-321640 (Bachatt's license)
- **AMC Partners**: SBI, ICICI, Axis, HDFC, Aditya Birla
- **Commission Model**: Trail commission from AMCs

### Key Metrics
| Metric | Value |
|--------|-------|
| Minimum SIP | ₹51/day |
| Expected Returns | 10-15% p.a. |
| Lock-in | None |
| Withdrawal | T+2 days |

---

## Regulatory & Compliance

### RBI Guidelines
- KYC mandatory for all financial products
- NACH mandate requires customer consent
- Digital lending guidelines for transparency
- Data localization requirements

### SEBI (for Bachatt)
- Mutual fund distributor registration
- Scheme document compliance
- NAV disclosure requirements
- Investor grievance handling

---

# 🤖 AI TOOLS & MODERN DEVELOPMENT

## Your AI Tool Usage

### Cursor AI
```
- Code exploration and understanding
- Test case generation
- Boilerplate scaffolding
- Documentation assistance
- Built ConfigNexus MCP server for AI-config integration
```

### How to Position
"I use AI tools for velocity without compromising quality:
1. **Exploration**: Quickly understand unfamiliar codebases
2. **Scaffolding**: Generate boilerplate, then refine
3. **Testing**: AI suggests edge cases I might miss
4. **Always Validate**: Human review before commit
5. **ConfigNexus**: Built MCP server for AI-config management"

---

# ❓ QUESTIONS TO ASK THE FOUNDER

## Technical Questions
1. "What's your biggest backend/infrastructure challenge today?"
2. "How do you handle UPI mandate failures at scale?"
3. "What's your observability stack? (Logging, monitoring, alerting)"

## Product Questions
4. "What's the 90-day roadmap for the engineering team?"
5. "How do you balance feature velocity vs. reliability?"
6. "What's the biggest compliance challenge you've faced?"

## Culture Questions
7. "How do you measure impact for engineers?"
8. "What does ownership look like here?"
9. "What's the team structure and collaboration model?"

---

# 📋 QUICK REFERENCE CHEAT SHEET

## Your Key Numbers
| Metric | Value |
|--------|-------|
| Experience | 5+ years |
| Partner Integrations | 5 (GPay, PhonePe, BharatPe, Paytm, Swiggy) |
| Latency Improvement | 20% (Redis caching) |
| Onboarding Reduction | 30% (State machine) |
| Services Built | 2 (NACH, InsureX) from scratch |

## Technologies
- **Backend**: Java, Spring Boot, JUnit, REST
- **Database**: MySQL, PostgreSQL
- **Caching**: Redis (Redisson)
- **Message Queue**: Kafka
- **Cloud**: AWS, GCP (basic)
- **AI Tools**: Cursor AI, ChatGPT, Gemini

## Design Patterns Used
- Factory Pattern (multi-vendor support)
- Strategy Pattern (NACH types, tenant-specific logic)
- State Machine (loan workflow)
- Builder Pattern (DTOs)

## Closing Line
"Thank you for the time. I'm excited about building reliable savings infrastructure at Bachatt. I enjoy ownership and shipping customer-visible outcomes, and I'd love to help scale the platform."

---

# 🎯 FINAL 30-MINUTE PREP CHECKLIST

- [ ] **5 min**: Practice 60-second intro (2x)
- [ ] **10 min**: Review NACH Service flow
- [ ] **10 min**: Fintech vocab drill
- [ ] **5 min**: Prepare 2-3 questions for founder

---

**Good luck! You've got this! 🚀**

*Document generated: January 15, 2026*
*For: Bachatt Co-founder Interview at 6 PM*
