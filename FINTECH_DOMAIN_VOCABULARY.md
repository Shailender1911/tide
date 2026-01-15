# 📚 Fintech & Lending Domain Vocabulary
## Comprehensive Reference for Technical Interviews

---

# 🏦 LENDING FUNDAMENTALS

## Loan Types

### 1. Term Loan (TL)
- Fixed amount borrowed for specific period
- EMI-based repayment
- Example: ₹5 Lakh for 24 months

### 2. Credit Line / Working Capital
- Revolving credit facility
- Draw as needed, pay interest only on used amount
- Example: ₹10 Lakh limit, use ₹3 Lakh, pay interest on ₹3 Lakh

### 3. Bullet Repayment Loan
- Principal repaid at end of tenure
- Interest may be paid monthly
- Common in short-term business loans

### 4. Invoice Financing / Factoring
- Loan against unpaid invoices
- Lender advances percentage of invoice value
- Repaid when customer pays invoice

### 5. Overdraft (OD)
- Credit attached to bank account
- Can withdraw more than balance
- Interest on overdrawn amount

---

## Loan Lifecycle Stages

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           LOAN LIFECYCLE                                     │
└─────────────────────────────────────────────────────────────────────────────┘

1. ORIGINATION
   └── Lead Generation → Application → Document Collection → Data Capture

2. UNDERWRITING
   └── KYC → Bureau Pull → Risk Assessment → Credit Scoring → Decision

3. SANCTIONING
   └── Offer Generation → Terms Finalization → Agreement → eSign

4. DISBURSEMENT
   └── NACH Registration → Penny Drop → Fund Transfer → Confirmation

5. SERVICING
   └── EMI Collection → Statement Generation → Customer Support

6. CLOSURE
   └── Final Payment → NOC Generation → Lien Release → Account Closure
```

---

## Key Loan Metrics

### LTV (Loan to Value)
```
LTV = (Loan Amount / Collateral Value) × 100
Example: ₹80 Lakh loan on ₹1 Cr property = 80% LTV
```

### EMI (Equated Monthly Installment)
```
EMI = P × r × (1+r)^n / ((1+r)^n - 1)
Where: P = Principal, r = monthly rate, n = tenure months
```

### ROI (Rate of Interest)
- **Flat Rate**: Interest on original principal
- **Reducing Balance**: Interest on outstanding principal
- **APR (Annual Percentage Rate)**: True cost including fees

### IRR (Internal Rate of Return)
- Actual return considering cash flow timing
- More accurate than nominal interest rate

### NPA (Non-Performing Asset)
- Loan where EMI unpaid for 90+ days
- Classification: SMA-0, SMA-1, SMA-2 → NPA

### DPD (Days Past Due)
```
DPD 0-30: On-time or minor delay
DPD 31-60: Early delinquency
DPD 61-90: Serious delinquency
DPD 90+: NPA category
```

---

# 💳 PAYMENT SYSTEMS

## NACH (National Automated Clearing House)

### Overview
- RBI's centralized clearing system
- Handles bulk/repetitive transactions
- Operated by NPCI (National Payments Corporation of India)

### NACH Types

#### 1. eNACH (Electronic)
```
Registration: Online via net banking/debit card
Mandate Limit: Up to ₹1 Crore
Speed: Same day activation
Customer Auth: OTP/Net banking
```

#### 2. Physical NACH
```
Registration: Paper mandate with wet signature
Mandate Limit: No limit
Speed: 10-15 business days
Customer Auth: Physical signature verification
```

#### 3. UPI NACH / UPI Autopay
```
Registration: Via UPI app (GPay, PhonePe, etc.)
Mandate Limit: Up to ₹1 Lakh per transaction
Speed: Instant activation
Customer Auth: UPI PIN
```

### NACH Lifecycle
```
CREATED → SUBMITTED → PENDING_VERIFICATION → APPROVED → ACTIVE → REVOKED/EXPIRED
                            ↓
                         REJECTED (with reason codes)
```

### Key NACH Terms
| Term | Definition |
|------|------------|
| **UMRN** | Unique Mandate Reference Number - unique identifier |
| **Sponsor Bank** | Lender's bank initiating debit |
| **Destination Bank** | Customer's bank account |
| **Mandate Amount** | Maximum debit amount per transaction |
| **Frequency** | As and when, monthly, quarterly, half-yearly, yearly |
| **Debit Type** | Fixed amount or maximum amount |

### NACH Response Codes
| Code | Meaning |
|------|---------|
| `00` | Success |
| `M2` | Invalid account number |
| `M3` | Account closed |
| `M4` | Insufficient funds |
| `M5` | Mandate cancelled by customer |

---

## UPI (Unified Payments Interface)

### Architecture
```
Customer → PSP App → NPCI → Remitter Bank → NPCI → Beneficiary Bank → Merchant
```

### UPI Identifiers
| Type | Example | Use Case |
|------|---------|----------|
| **VPA** | user@upi | Virtual Payment Address |
| **Mobile** | 9876543210@upi | Mobile-linked |
| **QR** | Encoded data | In-person payments |

### UPI Transaction Types
- **Collect**: Merchant requests payment from customer
- **Push**: Customer initiates payment to merchant
- **Autopay**: Recurring mandate-based payments

### UPI Limits (2024)
| Transaction Type | Limit |
|-----------------|-------|
| Regular UPI | ₹1 Lakh |
| UPI Lite | ₹500 per txn, ₹2000 wallet |
| Autopay | ₹1 Lakh per transaction |

---

## Other Payment Modes

### IMPS (Immediate Payment Service)
- Real-time 24x7 transfer
- Limit: ₹5 Lakh per transaction
- MMID + Mobile based or account number

### NEFT (National Electronic Funds Transfer)
- Batch-based settlement
- No minimum, no maximum limit
- Hourly batches (half-hourly on weekdays)

### RTGS (Real Time Gross Settlement)
- Real-time, for large value transactions
- Minimum: ₹2 Lakh
- No maximum limit

---

# 🔐 KYC & COMPLIANCE

## KYC Types

### 1. eKYC (Aadhaar-based)
```
Process: Aadhaar OTP verification
Time: 2-3 minutes
Limit: For small-value loans
Data: Name, DOB, Address, Photo from UIDAI
```

### 2. CKYC (Central KYC)
```
Registry: Central KYC Records Registry (CKYCR)
Identifier: 14-digit KIN (KYC Identification Number)
Use: One-time KYC, reusable across institutions
```

### 3. Video KYC (V-KYC)
```
Process: Live video call with verification officer
Time: 5-10 minutes
Compliance: RBI-mandated for certain products
Provider: Hyperverge, DigiLocker, etc.
```

### 4. Offline Aadhaar
```
Method: XML download or QR code scan
Privacy: Masked Aadhaar number
Consent: Customer-initiated sharing
```

## KYC Documents

### Identity Proof (any one)
- PAN Card ✓ (mandatory for loans)
- Aadhaar Card
- Passport
- Voter ID
- Driving License

### Address Proof (any one)
- Aadhaar Card
- Utility Bills (< 3 months)
- Bank Statement
- Rental Agreement

### Financial Documents
- Bank Statements (6-12 months)
- ITR (Income Tax Returns)
- GST Returns
- Salary Slips

---

## PAN Verification

### PAN Status Check
```
Valid PAN: ABCDE1234F (5 letters, 4 digits, 1 letter)
- 1st-3rd: Alphabetic series
- 4th: Holder type (P=Person, C=Company, F=Firm)
- 5th: First letter of surname
- 6th-9th: Sequential digits
- 10th: Alphabetic check digit
```

### PAN Verification APIs
- NSDL TIN
- UTIITSL
- Third-party aggregators (Karza, IDfy)

---

## Credit Bureau

### Indian Credit Bureaus
| Bureau | Full Name |
|--------|-----------|
| **CIBIL** | Credit Information Bureau (India) Limited |
| **Experian** | Experian Credit Information Company |
| **Equifax** | Equifax Credit Information Services |
| **CRIF High Mark** | CRIF High Mark Credit Information Services |

### CIBIL Score Ranges
| Range | Rating | Approval Likelihood |
|-------|--------|---------------------|
| 750-900 | Excellent | High |
| 700-749 | Good | Good |
| 650-699 | Fair | Moderate |
| 600-649 | Poor | Low |
| 300-599 | Very Poor | Very Low |
| -1 | No History | New to credit |

### Credit Report Components
```
1. Personal Information
   - Name, DOB, PAN, Address

2. Account Information
   - All credit accounts
   - Payment history
   - Credit utilization

3. Enquiry Information
   - Hard enquiries (loan applications)
   - Soft enquiries (self-checks)

4. DPD History
   - Month-wise payment status
   - Last 36 months typically
```

---

# 🏛️ REGULATORY FRAMEWORK

## RBI Guidelines

### Digital Lending Guidelines (2022)
- Disbursement directly to borrower's bank account
- No third-party collection agents without disclosure
- Loan agreement must be provided before disbursement
- Cooling-off period for certain loans
- Grievance redressal mechanism mandatory

### KYC Norms
- Risk-based KYC approach
- Periodic KYC updates required
- Video KYC permitted with safeguards

### Fair Practice Code
- Transparent interest rate disclosure
- No hidden charges
- Non-coercive recovery practices

### Data Localization
- Payment data must be stored in India
- Cross-border data transfer restrictions

---

## SEBI Regulations (For Bachatt)

### Mutual Fund Distributor
```
Registration: ARN (AMFI Registration Number)
Exam: NISM Series-V-A certification
Renewal: Every 3 years
Commission: Trail commission from AMCs (0.5-1%)
```

### Key Compliances
- KYC for all investors
- Risk profiling
- Scheme suitability assessment
- NAV disclosure requirements

---

# 📊 BUSINESS METRICS

## Lending Metrics

### Acquisition
| Metric | Formula |
|--------|---------|
| CAC | Total Marketing Cost / New Customers |
| Conversion Rate | Approved Loans / Total Applications |
| Lead to Disbursal | Disbursed Loans / Total Leads |

### Portfolio Health
| Metric | Formula |
|--------|---------|
| NPA Ratio | NPA Amount / Total Loans |
| PAR (Portfolio at Risk) | Loans with DPD > X / Total Loans |
| Collection Efficiency | Collected Amount / Due Amount |

### Profitability
| Metric | Formula |
|--------|---------|
| NIM | (Interest Income - Interest Expense) / Average Assets |
| ROA | Net Income / Total Assets |
| ROE | Net Income / Shareholders' Equity |

---

## Bachatt-Specific Metrics

### SIP Metrics
| Metric | Description |
|--------|-------------|
| **Daily Active Users** | Users with active daily SIP |
| **SIP Amount** | Average daily SIP amount |
| **Mandate Success Rate** | Successful mandates / Total created |
| **Churn Rate** | Users stopping SIP / Total users |

### Investment Metrics
| Metric | Description |
|--------|-------------|
| **AUM** | Assets Under Management |
| **NAV** | Net Asset Value per unit |
| **XIRR** | Extended Internal Rate of Return |
| **CAGR** | Compound Annual Growth Rate |

---

# 🔧 TECHNICAL TERMS

## Integration Patterns

### Webhook
```
Purpose: Real-time event notification
Security: HMAC signature verification
Retry: Exponential backoff
Idempotency: Unique event ID
```

### API Design
```
REST: Resource-based, stateless
GraphQL: Query flexibility
gRPC: High performance, binary protocol
```

### Message Queues
```
Kafka: High throughput, distributed
RabbitMQ: Feature-rich, routing
SQS: AWS managed, serverless
```

---

## Security Concepts

### Encryption
| Type | Use Case |
|------|----------|
| **AES-256** | Data at rest |
| **TLS 1.3** | Data in transit |
| **RSA** | Key exchange |
| **HMAC-SHA256** | Message authentication |

### Authentication
```
OAuth 2.0: Authorization framework
JWT: JSON Web Token for sessions
API Key: Service-to-service auth
mTLS: Mutual TLS for high security
```

---

# 📝 GLOSSARY

| Term | Full Form | Meaning |
|------|-----------|---------|
| **AML** | Anti-Money Laundering | Regulations to prevent money laundering |
| **API** | Application Programming Interface | Service communication interface |
| **ARN** | AMFI Registration Number | MF distributor identifier |
| **CIBIL** | Credit Information Bureau | Credit scoring agency |
| **COI** | Certificate of Insurance | Insurance proof document |
| **DPD** | Days Past Due | Overdue days count |
| **EMI** | Equated Monthly Installment | Fixed monthly payment |
| **eNACH** | Electronic NACH | Digital mandate registration |
| **FLDG** | First Loss Default Guarantee | Risk-sharing mechanism |
| **GST** | Goods and Services Tax | Indirect tax |
| **IMPS** | Immediate Payment Service | Real-time transfer |
| **IRR** | Internal Rate of Return | True return rate |
| **ITR** | Income Tax Return | Tax filing document |
| **KFS** | Key Fact Statement | Loan terms summary |
| **KYC** | Know Your Customer | Customer verification |
| **LMS** | Loan Management System | Loan lifecycle software |
| **LOS** | Loan Origination System | Loan application software |
| **LTV** | Loan to Value | Loan vs collateral ratio |
| **NACH** | National Automated Clearing House | Recurring payment system |
| **NAV** | Net Asset Value | MF unit value |
| **NEFT** | National Electronic Funds Transfer | Batch transfer |
| **NPA** | Non-Performing Asset | Bad loan |
| **NPCI** | National Payments Corporation | Payments infrastructure |
| **OTP** | One Time Password | Verification code |
| **PAN** | Permanent Account Number | Tax identifier |
| **PAR** | Portfolio at Risk | At-risk loan percentage |
| **PSP** | Payment Service Provider | UPI app provider |
| **ROI** | Rate of Interest | Interest rate |
| **RTGS** | Real Time Gross Settlement | Large value transfer |
| **SIP** | Systematic Investment Plan | Regular investment |
| **SMA** | Special Mention Account | Pre-NPA category |
| **UIDAI** | Unique ID Authority of India | Aadhaar authority |
| **UMRN** | Unique Mandate Reference Number | Mandate identifier |
| **UPI** | Unified Payments Interface | Instant payment |
| **VPA** | Virtual Payment Address | UPI address |

---

# 🎯 INTERVIEW TIPS

## When Asked About Domain Knowledge

✅ **Do**:
- Use correct terminology
- Explain with examples from your projects
- Connect to business impact
- Acknowledge what you don't know

❌ **Don't**:
- Fake knowledge
- Over-complicate explanations
- Forget the business context

## Sample Answer Structure
```
"NACH is the National Automated Clearing House - RBI's system for recurring payments.

In my NACH service, we support three types:
1. UPI NACH - instant, up to ₹1 lakh, customer uses UPI PIN
2. eNACH - via net banking, up to ₹1 crore
3. Physical - paper mandate, no limit

For Bachatt's daily SIP, UPI NACH is most relevant because it's 
instant and perfect for small, recurring amounts."
```

---

*Last Updated: January 15, 2026*
