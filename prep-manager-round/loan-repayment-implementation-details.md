# 🏦 Tide Interview - Loan Repayment Service Deep Dive

**Highly Relevant for FinTech (Tide)**: This service handles the complete loan lifecycle post-disbursement - payments, settlements, ENACH collections, and LMS integration.

---

## 📋 Table of Contents

1. [Service Overview](#service-overview)
2. [Architecture Deep Dive](#architecture-deep-dive)
3. [Core Features](#core-features)
4. [Payment Modes](#payment-modes)
5. [Key Flows](#key-flows)
6. [Technical Implementation](#technical-implementation)
7. [Integrations](#integrations)
8. [How to Present](#how-to-present)
9. [Expected Questions & Answers](#expected-questions--answers)

---

## 🎯 Service Overview

### **30-Second Pitch**

> "The Loan Repayment Service is the post-disbursement backbone of PayU's Digital Lending Suite. It handles complete loan lifecycle management including multiple payment modes (Virtual Account, ENACH, Payment Links, UPI Autopay), settlement processing between lender and merchant, integration with Finflux LMS for loan management, and automated repayment scheduling. The service supports multiple lending partners like Google Pay, PhonePe, and BharatPe with channel-specific configurations."

### **What This Service Does**

```
┌─────────────────────────────────────────────────────────────────┐
│                   LOAN REPAYMENT SERVICE                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  🏦 Loan Lifecycle Management                                    │
│  ├── Loan Creation in LMS (Finflux)                             │
│  ├── Disbursement Processing                                     │
│  ├── Repayment Collection                                        │
│  ├── Settlement Distribution                                     │
│  └── Loan Closure / Foreclosure                                  │
│                                                                   │
│  💳 Payment Collection                                           │
│  ├── Virtual Account (VA/Payout)                                │
│  ├── ENACH Mandate Debit                                        │
│  ├── Payment Links (PG)                                         │
│  ├── UPI Autopay                                                │
│  └── Manual NEFT/IMPS                                           │
│                                                                   │
│  💰 Settlement Processing                                        │
│  ├── Lender Settlement                                          │
│  ├── Merchant Settlement                                        │
│  └── Split Payment Analysis                                     │
│                                                                   │
│  📊 LMS Integration (Finflux)                                   │
│  ├── Loan Demand Calculation                                    │
│  ├── Repayment Posting                                          │
│  └── Loan Status Sync                                           │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Architecture Deep Dive

### **High-Level Architecture**

```
┌─────────────────────────────────────────────────────────────────┐
│                    External Partners                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Google   │ │ PhonePe  │ │ BharatPe │ │  Paytm   │          │
│  │ Pay      │ │          │ │          │ │          │          │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘          │
│       └────────────┴────────────┴────────────┘                  │
│                           │                                       │
│                           ▼                                       │
│  ┌────────────────────────────────────────────────────────────┐│
│  │              LOAN REPAYMENT SERVICE                        ││
│  │                                                             ││
│  │  ┌─────────────────────────────────────────────────────┐  ││
│  │  │               Controller Layer                       │  ││
│  │  │  LoanController │ PaymentController │ MandateCtrl   │  ││
│  │  │  PayoutController │ CallbackController │ CronCtrl   │  ││
│  │  └─────────────────────────────────────────────────────┘  ││
│  │                           │                                 ││
│  │  ┌─────────────────────────────────────────────────────┐  ││
│  │  │               Service Layer                          │  ││
│  │  │  LoanService │ RepaymentService │ SettlementService │  ││
│  │  │  LMSService │ ENACHService │ PayoutWebhookService   │  ││
│  │  └─────────────────────────────────────────────────────┘  ││
│  │                           │                                 ││
│  │  ┌─────────────────────────────────────────────────────┐  ││
│  │  │               Repository Layer                       │  ││
│  │  │  LoanRepository │ LoanPaymentRepository              │  ││
│  │  │  MerchantSettlementRepository │ ApplicationInfoRepo │  ││
│  │  └─────────────────────────────────────────────────────┘  ││
│  │                                                             ││
│  └────────────────────────────────────────────────────────────┘│
│                           │                                       │
│       ┌───────────────────┼───────────────────┐                 │
│       ▼                   ▼                   ▼                 │
│  ┌──────────┐      ┌──────────┐       ┌──────────┐            │
│  │ Finflux  │      │  PayU    │       │  ENACH   │            │
│  │  (LMS)   │      │ Payout   │       │ Service  │            │
│  └──────────┘      └──────────┘       └──────────┘            │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### **Data Model**

```
┌─────────────────────────────────────────────────────────────────┐
│                       Core Entities                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ApplicationInfo (1) ──────────────────────────┐                │
│  │ - applicationId                              │                │
│  │ - payoutMerchantId (VA Account)             │                │
│  │ - channelInfo (Partner Config)              │                │
│  │ - bankAccountId                             │                │
│  │                                              │                │
│  └──────────┬───────────────────────────────────┘                │
│             │ (1:N)                                               │
│             ▼                                                     │
│  ┌─────────────────────┐    ┌─────────────────────┐            │
│  │       Loan          │    │   ENACHMandate      │            │
│  │ - lmsLoanId         │    │ - mandateId         │            │
│  │ - status            │    │ - umrn              │            │
│  │ - disbursedAmount   │    │ - status            │            │
│  │ - pendingAmount     │    │ - nachType          │            │
│  │ - dpdDays           │    └─────────────────────┘            │
│  └─────────┬───────────┘                                        │
│            │ (1:N)                                               │
│            ▼                                                     │
│  ┌─────────────────────┐    ┌─────────────────────┐            │
│  │    LoanPayment      │    │ MerchantSettlement  │            │
│  │ - amountPaid        │    │ - amount            │            │
│  │ - paymentMode       │    │ - status            │            │
│  │ - status            │    │ - bankAccountId     │            │
│  │ - splitId           │    │ - splitId           │            │
│  └─────────────────────┘    └─────────────────────┘            │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              RepaymentsSchedule                              ││
│  │ - repaymentDate                                              ││
│  │ - fixedAmount / adjustedAmount                               ││
│  │ - paymentMode                                                ││
│  │ - status (CREATED, INITIATED, SUCCESS, FAILED)              ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Core Features

### **1. Multi-Mode Payment Collection**

The service supports multiple payment modes for loan repayment:

| Payment Mode | Description | Use Case |
|--------------|-------------|----------|
| **PAYOUT (VA)** | Virtual Account collection | Merchant daily sales collected via VA |
| **ENACH** | Bank mandate auto-debit | Scheduled EMI collection |
| **Payment Link** | PG-based payment link | Customer-initiated payment |
| **UPI Autopay** | UPI mandate auto-debit | Modern mandate collection |
| **NEFT/IMPS** | Manual bank transfer | Manual payments |

### **2. Settlement Processing**

```
Total Payment Received
         │
         ▼
┌─────────────────────────┐
│  Split Calculation      │
│  (Based on Demand)      │
└─────────────────────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌─────────────┐
│Lender │ │  Merchant   │
│(EMI)  │ │(Excess Amt) │
└───────┘ └─────────────┘
    │           │
    ▼           ▼
┌───────┐ ┌─────────────┐
│LMS    │ │ Bank        │
│Posting│ │ Transfer    │
└───────┘ └─────────────┘
```

### **3. LMS Integration (Finflux)**

**Key Operations:**
- **Loan Creation**: Create loan in Finflux after application approval
- **Disbursement**: Disburse loan and update status
- **Demand Fetch**: Get current loan demand (principal + interest + charges)
- **Repayment Posting**: Post successful payments to LMS
- **Status Sync**: Sync loan status with LMS

### **4. Repayment Schedule Management**

```java
// Daily repayment processing flow
1. Fetch applications with repayment due today
2. For each application:
   a. Get VA account balance (PayU Payout)
   b. Fetch loan demand from LMS (Finflux)
   c. Calculate split:
      - If balance >= demand: Pay full demand
      - If balance < demand: Adjust payment (cutoff logic)
   d. Create LoanPayment record
   e. Create MerchantSettlement for excess
   f. Initiate payout to lender
```

---

## 💳 Payment Modes Deep Dive

### **1. Virtual Account (VA/Payout) Collection**

**How It Works:**
1. Each merchant gets a Virtual Account (VA)
2. Daily sales/revenue deposited to VA
3. At cutoff time, system:
   - Checks VA balance
   - Calculates loan demand
   - Debits EMI amount
   - Settles excess to merchant

```java
// VA Repayment Flow
public void checkAvailableAmountAndInitiatePayment(ApplicationInfo appInfo, 
                                                   LocalDate repaymentDate) {
    // 1. Get VA balance
    MerchantAccountDetailResponse vaDetails = payoutClient
        .getMerchantAccountDetails(token, appInfo.getPayoutMerchantId());
    double vaBalance = vaDetails.getData().getBalance();
    
    // 2. Get scheduled repayments for today
    List<RepaymentsSchedule> upcomingPayments = repaymentsScheduleRepository
        .findByApplicationIdAndRepaymentDate(appInfo.getApplicationId(), repaymentDate);
    
    // 3. Validate loans and calculate required amount
    List<RepaymentsSchedule> validRepayments = getValidRepaymentsSchedule(upcomingPayments);
    double requiredAmount = validRepayments.stream()
        .mapToDouble(RepaymentsSchedule::getFixedAmount).sum();
    
    // 4. Process based on balance vs required
    if (vaBalance >= requiredAmount) {
        // Full payment possible
        processPayoutRepayments(validRepayments, false, appInfo, vaBalance);
    } else if (!isCutoffTimeReached()) {
        // Partial payment with adjustment
        List<RepaymentsSchedule> adjustedPayments = splitPaymentAnalyzer
            .adjustUpcomingPayments(loans, validRepayments, vaBalance);
        processPayoutRepayments(adjustedPayments, true, appInfo, vaBalance);
    }
    
    // 5. Settle excess to merchant
    createSettlementPayment(vaBalance, requiredAmount, appInfo);
}
```

### **2. ENACH Mandate Collection**

**How It Works:**
1. Customer registers ENACH mandate during loan setup
2. Mandate has UMRN (Unique Mandate Reference Number)
3. On due date, system presents collection request
4. Bank debits customer account

```java
// ENACH Collection Flow
public LoanPayment initiateEnachPayment(ENACHMandate mandate, 
                                        LoanPayment loanPayment, 
                                        Loan loan) {
    // 1. Build payment request
    PaymentRequest request = PaymentRequest.builder()
        .authpayuid(mandate.getMihpayid())  // Mandate ID
        .amount(loanPayment.getAmountPaid())
        .txnid(loanPayment.getPaymentReferenceNumber())
        .build();
    
    // 2. Call ENACH service
    Response response = restCallsUtil.makeServiceCall(
        ENACH_INITIATE_PAYMENT, HttpMethod.POST, request);
    
    // 3. Handle response
    if (response.isSuccess()) {
        // Payment initiated - wait for callback
        loanPayment.setStatus(PENDING);
    } else {
        loanPayment.setStatus(FAILED);
    }
    
    return loanPaymentRepository.save(loanPayment);
}

// ENACH Callback Handler
public void receivePaymentResponse(ENACHResponse response) {
    LoanPayment payment = loanPaymentRepository
        .findByPaymentReferenceNumber(response.getTxnid());
    
    if (response.isSuccess()) {
        payment.setStatus(SUCCESS);
        // Post to LMS
        postRepaymentToLMS(payment);
    } else {
        payment.setStatus(FAILED);
        // Retry or alert
    }
    loanPaymentRepository.save(payment);
}
```

### **3. Split Payment Analysis**

**Problem:** VA balance may be less than total loan demand.  
**Solution:** Smart split across multiple loans based on priority.

```java
// Split Payment Logic
public List<RepaymentsSchedule> adjustUpcomingPayments(
        List<Loan> loans, 
        List<RepaymentsSchedule> repayments, 
        double availableBalance) {
    
    // Sort by priority (e.g., DPD days, interest rate)
    repayments.sort(Comparator.comparing(r -> {
        Loan loan = findLoan(loans, r.getLmsLoanId());
        return loan.getDpdDays();  // Higher DPD = higher priority
    }).reversed());
    
    double remainingBalance = availableBalance;
    List<RepaymentsSchedule> adjustedPayments = new ArrayList<>();
    
    for (RepaymentsSchedule repayment : repayments) {
        if (remainingBalance <= 0) break;
        
        double payableAmount = Math.min(repayment.getFixedAmount(), remainingBalance);
        repayment.setAdjustedAmount(payableAmount);
        adjustedPayments.add(repayment);
        
        remainingBalance -= payableAmount;
    }
    
    return adjustedPayments;
}
```

---

## 🔄 Key Flows

### **Flow 1: Daily VA Repayment Processing**

```
┌──────────────────────────────────────────────────────────────────┐
│                DAILY VA REPAYMENT CRON JOB                        │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  1. Fetch all applications with repayment due today               │
│     │                                                              │
│     ▼                                                              │
│  2. For each application:                                          │
│     ├── Get VA balance from PayU Payout                           │
│     ├── Fetch loan demand from Finflux LMS                        │
│     ├── Check: balance > 0 AND no pending payments?               │
│     │                                                              │
│     ▼                                                              │
│  3. If YES:                                                        │
│     ├── Get scheduled repayments for today                        │
│     ├── Validate loans (check status = DISBURSED)                 │
│     │                                                              │
│     ▼                                                              │
│  4. Calculate split:                                               │
│     ├── If balance >= demand: Full payment                        │
│     ├── If balance < demand AND before cutoff: Adjusted payment   │
│     ├── If after cutoff: Skip (next day processing)               │
│     │                                                              │
│     ▼                                                              │
│  5. Create records:                                                │
│     ├── LoanPayment (EMI to lender)                               │
│     ├── MerchantSettlement (excess to merchant)                   │
│     │                                                              │
│     ▼                                                              │
│  6. Initiate payouts:                                              │
│     ├── Post LoanPayment to Payout service                        │
│     ├── Post MerchantSettlement to Payout service                 │
│     │                                                              │
│     ▼                                                              │
│  7. Wait for payout webhook callbacks                             │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

### **Flow 2: LMS Repayment Posting**

```
┌──────────────────────────────────────────────────────────────────┐
│              LMS REPAYMENT POSTING CRON JOB                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  1. Fetch all successful LoanPayments not posted to LMS           │
│     WHERE status = 'SUCCESS' AND lmsPostingStatus != 'POSTED'     │
│     │                                                              │
│     ▼                                                              │
│  2. For each payment:                                              │
│     ├── Build LoanRepaymentRequest                                │
│     ├── Set paymentTypeId based on payment mode                   │
│     │                                                              │
│     ▼                                                              │
│  3. Call Finflux API:                                              │
│     finfluxClient.postLoanRepayment(request, lmsLoanId)           │
│     │                                                              │
│     ▼                                                              │
│  4. Handle response:                                               │
│     ├── Success: Mark as SUCCESSFULLY_POSTED_TO_LMS               │
│     ├── Failure: Mark as FAILED_TO_POST_TO_LMS (retry later)      │
│     │                                                              │
│     ▼                                                              │
│  5. Update loan status:                                            │
│     ├── If fully paid: Set loan status = CLOSED                   │
│     ├── Update pendingAmount, dpdDays                             │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

### **Flow 3: Payout Webhook Processing**

```
┌──────────────────────────────────────────────────────────────────┐
│                  PAYOUT WEBHOOK HANDLER                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  Webhook Types:                                                    │
│  ├── VA_DEPOSIT: New funds deposited to VA                        │
│  ├── PAYOUT_SUCCESS: Payout to bank successful                    │
│  ├── PAYOUT_FAILURE: Payout to bank failed                        │
│                                                                    │
│  VA_DEPOSIT Flow:                                                  │
│  1. Parse webhook payload                                          │
│  2. Identify application by merchantId                            │
│  3. If auto-debit enabled:                                         │
│     ├── Fetch loan demand                                         │
│     ├── Create LoanPayment                                        │
│     ├── Initiate payout to lender                                 │
│                                                                    │
│  PAYOUT_SUCCESS Flow:                                              │
│  1. Parse webhook payload                                          │
│  2. Find LoanPayment/MerchantSettlement by reference              │
│  3. Update status = SUCCESS                                        │
│  4. Trigger LMS posting                                            │
│                                                                    │
│  PAYOUT_FAILURE Flow:                                              │
│  1. Parse webhook payload                                          │
│  2. Find LoanPayment/MerchantSettlement by reference              │
│  3. Update status = FAILED                                         │
│  4. Retry or alert                                                 │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🔌 Integrations

### **1. Finflux (LMS - Loan Management System)**

**Purpose:** Core loan ledger management

**Key APIs:**
| API | Purpose |
|-----|---------|
| `createLoan` | Create new loan in LMS |
| `disburseLoan` | Disburse loan |
| `fetchLoanRepaymentDemandV3` | Get current demand |
| `postLoanRepayment` | Post repayment transaction |
| `getLoanForLoanDetailsV2` | Get loan details |

```java
// Finflux LMS Integration
@Service
public class LMSServiceImpl implements LMSService {
    
    @Autowired
    private FinfluxClient finfluxClient;
    
    // Create loan in LMS
    public Response submitLoanApplication(Loan loan, List<Charges> charges) {
        CreateLoanRequest request = buildCreateLoanRequest(loan, charges);
        CreateLoanResponse response = finfluxClient.createLoan(request);
        
        loan.setLmsLoanId(String.valueOf(response.getLoanId()));
        loan.setStatus(LoanStatus.APPLIED);
        return loanRepository.save(loan);
    }
    
    // Fetch demand from LMS
    public FetchDemandResponse fetchLoanDemand(String lmsLoanId, String date) {
        return finfluxClient.fetchDemand(lmsLoanId, date);
    }
    
    // Post repayment to LMS
    public void postRepaymentToLMS(LoanPayment payment, Loan loan) {
        LoanRepaymentRequest request = LoanRepaymentRequest.builder()
            .transactionAmount(payment.getAmountPaid())
            .transactionDate(payment.getReceivedDate().toString())
            .externalId(payment.getPaymentReferenceNumber())
            .paymentTypeId(getPaymentTypeId(payment.getPaymentMode()))
            .build();
        
        finfluxClient.postLoanRepayment(request, loan.getLmsLoanId());
    }
}
```

### **2. PayU Payout (Virtual Account)**

**Purpose:** VA management and fund transfers

**Key APIs:**
| API | Purpose |
|-----|---------|
| `getMerchantAccountDetails` | Get VA balance |
| `processPayment` | Initiate payout |
| `getVaCreditDetails` | Get VA deposits |

```java
// PayU Payout Integration
@Service
public class PayoutServiceImpl implements PayoutService {
    
    @Autowired
    private PayoutClient payoutClient;
    
    // Get VA balance
    public double getVABalance(String merchantId) {
        MerchantAccountDetailResponse response = payoutClient
            .getMerchantAccountDetails(token, merchantId);
        return response.getData().getBalance();
    }
    
    // Initiate payout
    public PaymentResponse processPayment(String merchantId, 
                                         List<PaymentRequest> requests) {
        return payoutClient.processPayment(token, merchantId, requests);
    }
}
```

### **3. ENACH Service**

**Purpose:** Mandate-based auto-debit collection

```java
// ENACH Integration
@Service
public class ENACHPaymentServiceImpl implements ENACHPaymentService {
    
    // Initiate ENACH debit
    public LoanPayment initiateEnachPayment(ENACHMandate mandate, 
                                           LoanPayment payment) {
        PaymentRequest request = buildPaymentRequest(mandate, payment);
        Response response = enachClient.initiatePayment(request);
        
        if (response.isSuccess()) {
            payment.setStatus(PENDING);
        } else {
            payment.setStatus(FAILED);
        }
        return loanPaymentRepository.save(payment);
    }
}
```

---

## 🎤 How to Present

### **Presentation Structure (7-10 minutes)**

#### **1. Problem Statement (1 min)**
> "After loan disbursement, we needed a robust system to handle:
> - Multiple payment collection methods (VA, ENACH, Payment Links)
> - Fair settlement distribution between lender and merchant
> - Integration with Loan Management System (Finflux)
> - Multi-partner support with different configurations"

#### **2. Solution Overview (2 min)**
> "The Loan Repayment Service handles post-disbursement lifecycle:
> - Supports 8+ payment modes
> - Processes daily VA collections with smart split logic
> - Integrates with Finflux LMS for demand and posting
> - Handles ENACH mandate collections
> - Manages merchant settlements"

#### **3. Technical Deep Dive (3-4 min)**

**Pick 2-3 of these to explain:**

1. **VA Repayment Flow**: Daily cron, balance check, demand fetch, split calculation
2. **Settlement Split Logic**: How we distribute between lender and merchant
3. **LMS Integration**: Demand fetch, repayment posting, status sync
4. **ENACH Flow**: Mandate registration, collection initiation, callback handling
5. **Cutoff Time Management**: How we handle insufficient funds scenarios

#### **4. Impact (1 min)**
- Processes lakhs of repayments daily
- Supports 5+ lending partners
- Automated settlement reduces manual intervention
- LMS posting ensures accurate loan ledger

### **STAR Method Example**

| STAR | Description |
|------|-------------|
| **S** | After disbursement, loans needed automated repayment collection, settlement distribution, and LMS integration |
| **T** | Build a robust repayment service supporting multiple payment modes and partner configurations |
| **A** | Implemented VA collection with smart split logic, ENACH integration, Finflux LMS integration, webhook handling |
| **R** | Service handles daily repayments for multiple partners with automated settlement and accurate LMS posting |

---

## ❓ Expected Questions & Answers

### **Business Logic Questions**

**Q: How do you handle partial payments when VA balance is less than loan demand?**
> "We have a split payment analyzer that:
> 1. Sorts loans by priority (DPD days, interest rate)
> 2. Allocates available balance across loans
> 3. Creates adjusted payments for each loan
> 4. If before cutoff time, processes partial payment
> 5. If after cutoff, defers to next day
> 
> This ensures high-priority loans (higher DPD) get paid first."

**Q: How do you ensure money goes to the right place?**
> "Three-way settlement:
> 1. **LoanPayment**: EMI amount to lender
> 2. **MerchantSettlement**: Excess amount to merchant bank account
> 3. **Split ID**: Links all related transactions for audit
> 
> Each has its own status tracking and payout processing."

**Q: What happens if LMS posting fails?**
> "We have a cron job that:
> 1. Fetches payments with status FAILED_TO_POST_TO_LMS
> 2. Retries posting with exponential backoff
> 3. After max retries, alerts operations team
> 4. Payment is marked for manual intervention
> 
> This ensures loan ledger eventually becomes consistent."

### **Technical Questions**

**Q: How do you handle concurrent payments for same loan?**
> "Multiple safeguards:
> 1. Check for existing INITIATED payments before creating new
> 2. Database constraints prevent duplicate entries
> 3. Optimistic locking on loan record
> 4. Batch ID (splitId) groups related transactions"

**Q: How do you ensure idempotency in payment processing?**
> "Several mechanisms:
> 1. Check existing payment by applicationId + date + paymentMode
> 2. Unique external reference number per payment
> 3. Status checks before processing (only process CREATED/FAILED)
> 4. Transaction boundaries with proper rollback"

**Q: How does the ENACH callback work?**
> "Async webhook pattern:
> 1. We initiate ENACH debit request
> 2. Payment status set to PENDING
> 3. ENACH service processes with bank
> 4. Bank responds to ENACH service
> 5. ENACH service sends webhook to us
> 6. We update payment status and post to LMS
> 
> We also have a cron to poll for stuck payments."

### **System Design Questions**

**Q: How would you scale this service?**
> "Current architecture supports scaling:
> 1. Stateless service - can add more instances
> 2. Async processing with thread pools
> 3. Database read replicas for read-heavy operations
> 4. Cron jobs with batch processing
> 5. Webhook handlers are idempotent
> 
> For higher scale, we could add:
> - Message queue (Kafka) for payment events
> - Caching for frequently accessed data
> - Sharding by partner/channel"

**Q: How do you handle failures in distributed transactions?**
> "Saga pattern with compensation:
> 1. Create LoanPayment (local)
> 2. Initiate Payout (external)
> 3. If payout fails: Mark payment FAILED, retry via cron
> 4. On payout success webhook: Update status, post to LMS
> 5. If LMS fails: Mark FAILED_TO_POST, retry via cron
> 
> Each step is independently retriable."

---

## 📊 Key Numbers to Remember

| Metric | Value |
|--------|-------|
| Payment Modes | 8+ (VA, ENACH, PG, UPI, etc.) |
| Partners Supported | 5+ (GPay, PhonePe, BharatPe, etc.) |
| Daily Processing | Thousands of repayments |
| LMS Integration | Finflux (demand, posting, sync) |
| Settlement Types | 2 (Lender, Merchant) |

---

## 🔑 Key Technical Terms

- **VA (Virtual Account)**: Dedicated account for merchant to receive funds
- **UMRN**: Unique Mandate Reference Number (ENACH identifier)
- **DPD**: Days Past Due (overdue days)
- **LMS**: Loan Management System (Finflux)
- **Split ID**: Batch identifier linking related transactions
- **Demand**: Current amount due (principal + interest + charges)
- **Cutoff Time**: Deadline for same-day processing

---

## ✅ Quick Revision Checklist

- [ ] VA repayment flow (balance check → demand fetch → split → payout)
- [ ] Settlement split logic (lender vs merchant)
- [ ] LMS integration (demand fetch, repayment posting)
- [ ] ENACH flow (mandate → initiate → callback)
- [ ] Payment modes and their use cases
- [ ] Error handling and retry mechanisms
- [ ] Idempotency in payment processing

---

**Good luck! 🚀**

This service is **highly relevant for Tide** - it demonstrates:
- Financial domain expertise
- Payment processing knowledge
- Integration patterns
- Transaction handling
- Error recovery strategies

