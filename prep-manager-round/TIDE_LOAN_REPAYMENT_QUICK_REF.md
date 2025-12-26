# 🎯 Loan Repayment - Quick Reference Card

**For Tide Interview - Last Minute Revision**

---

## 🏦 30-Second Pitch

> "Loan Repayment Service is the post-disbursement backbone handling:
> - **8+ payment modes** (VA, ENACH, Payment Links, UPI Autopay)
> - **Settlement distribution** between lender and merchant
> - **LMS integration** with Finflux for demand and posting
> - **Multi-partner support** (GPay, PhonePe, BharatPe)"

---

## 🔑 Key Components

| Component | Purpose |
|-----------|---------|
| **RepaymentService** | VA-based daily repayment processing |
| **PayoutWebhookService** | Handle VA deposit webhooks |
| **ENACHPaymentService** | Mandate-based auto-debit |
| **LMSService** | Finflux integration (loan, demand, posting) |
| **SettlementService** | Merchant payout processing |
| **SplitPaymentAnalyzer** | Smart split when balance < demand |

---

## 💳 Payment Modes

```
PAYOUT (VA)     → Merchant VA daily collection
ENACH           → Bank mandate auto-debit
Payment Link    → Customer-initiated PG payment
UPI Autopay     → UPI mandate collection
NEFT/IMPS       → Manual bank transfer
```

---

## 🔄 VA Repayment Flow (MOST IMPORTANT)

```
1. CRON triggers at scheduled time
2. Get VA balance (PayU Payout API)
3. Fetch loan demand (Finflux LMS API)
4. Check: balance > 0 AND no pending payments?
5. Calculate split:
   • If balance >= demand → Full payment
   • If balance < demand → Adjusted payment
6. Create LoanPayment (EMI to lender)
7. Create MerchantSettlement (excess to merchant)
8. Initiate payouts
9. Wait for webhook callbacks
10. Post to LMS on success
```

---

## 📊 Settlement Split Logic

```
┌─────────────────────────────────────┐
│   VA Balance = ₹10,000              │
│   Loan Demand = ₹6,000              │
├─────────────────────────────────────┤
│   LoanPayment = ₹6,000 (to lender)  │
│   MerchantSettlement = ₹4,000       │
│   (to merchant bank account)        │
└─────────────────────────────────────┘
```

---

## 🔌 Integrations

| System | Purpose | Key APIs |
|--------|---------|----------|
| **Finflux (LMS)** | Loan ledger | `createLoan`, `fetchDemand`, `postRepayment`, `precloseLoan` |
| **PayU Payout** | VA management | `getMerchantAccountDetails`, `processPayment` |
| **ENACH Service** | Mandate debit | `initiatePayment`, webhook callback |

---

## 📝 Key Entities

```
ApplicationInfo (1)
    │
    ├── Loan (1:N) ──── LoanPayment (1:N)
    │
    ├── ENACHMandate (1:N)
    │
    └── MerchantSettlement (1:N)

RepaymentsSchedule → Tracks daily scheduled repayments
```

---

## ⚡ Key Code Pattern - VA Repayment

```java
// Core logic in RepaymentServiceImpl
public void checkAvailableAmountAndInitiatePayment(...) {
    // 1. Get VA balance
    double vaBalance = payoutClient.getMerchantAccountDetails(merchantId)
        .getData().getBalance();
    
    // 2. Get scheduled repayments
    List<RepaymentsSchedule> repayments = repository
        .findByApplicationIdAndRepaymentDate(appId, today);
    
    // 3. Calculate required amount
    double requiredAmount = repayments.stream()
        .mapToDouble(r -> r.getFixedAmount()).sum();
    
    // 4. Process based on balance
    if (vaBalance >= requiredAmount) {
        processPayoutRepayments(repayments, appInfo, vaBalance);
    } else if (!isCutoffTimeReached()) {
        List<RepaymentsSchedule> adjusted = splitPaymentAnalyzer
            .adjustUpcomingPayments(loans, repayments, vaBalance);
        processPayoutRepayments(adjusted, appInfo, vaBalance);
    }
    
    // 5. Create merchant settlement for excess
    createSettlementPayment(vaBalance, requiredAmount, appInfo);
}
```

---

## 🔒 Key Technical Challenges & Solutions

| Challenge | Solution |
|-----------|----------|
| **Concurrent payments** | Check existing INITIATED payments before creating |
| **Idempotency** | Unique external reference + status checks |
| **LMS posting failure** | Retry cron job for FAILED_TO_POST_TO_LMS |
| **Insufficient funds** | Split analyzer + cutoff time logic |
| **Webhook duplicates** | Check by transferId before processing |

---

## 📈 Key Numbers

| Metric | Value |
|--------|-------|
| Payment Modes | 8+ |
| Partners | 5+ (GPay, PhonePe, BharatPe, Paytm, Swiggy) |
| Integrations | Finflux, PayU Payout, ENACH |
| Settlement Types | 2 (Lender, Merchant) |

---

## ❓ Top Interview Questions

### 1. How does VA repayment work?
> Daily cron → Get balance → Fetch demand → Split → Create payments → Payout → Webhook → LMS posting

### 2. How do you handle partial payments?
> SplitPaymentAnalyzer sorts by priority (DPD), allocates available balance, processes adjusted payments

### 3. What happens if LMS posting fails?
> Marked as FAILED_TO_POST_TO_LMS → Retry cron picks up → Exponential backoff → Manual intervention after max retries

### 4. How is settlement split calculated?
> Total VA balance - Loan demand = Merchant settlement (excess goes to merchant)

### 5. How do you ensure idempotency?
> Check existing payments by appId+date+mode, unique externalRef, status checks before processing

---

## 🎯 STAR Format

| | |
|---|---|
| **S** | Post-disbursement loans needed automated collection, settlement distribution, LMS integration |
| **T** | Build repayment service supporting multiple payment modes, partner configs |
| **A** | Implemented VA collection, ENACH, split logic, Finflux integration, webhook handling |
| **R** | Automated daily repayments for multiple partners with accurate settlements and LMS posting |

---

## 🏷️ Technical Terms

- **VA**: Virtual Account (merchant collection account)
- **UMRN**: Unique Mandate Reference Number (ENACH)
- **DPD**: Days Past Due (overdue days)
- **LMS**: Loan Management System (Finflux)
- **Split ID**: Batch identifier linking transactions
- **Demand**: Current amount due (principal + interest + charges)
- **Cutoff Time**: Deadline for same-day processing

---

**Remember: This is HIGHLY RELEVANT for Tide (FinTech) - demonstrates financial domain expertise, payment processing, and transaction handling!**

**Good luck! 🚀**

