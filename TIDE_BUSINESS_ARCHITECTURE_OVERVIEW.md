# 🏢 PAYु LENDING - BUSINESS & ARCHITECTURE OVERVIEW

**For Tide Interview - Business Context & Technical Architecture**

---

## 📋 TABLE OF CONTENTS

1. [The Business - What We Do](#1-the-business---what-we-do)
2. [The Architecture - How We Do It](#2-the-architecture---how-we-do-it)
3. [Product Portfolio](#3-product-portfolio)
4. [Partner Ecosystem](#4-partner-ecosystem)
5. [Technical Stack](#5-technical-stack)
6. [Team Structure & Your Role](#6-team-structure--your-role)
7. [Business Metrics](#7-business-metrics)
8. [Interview Talking Points](#8-interview-talking-points)

---

## 1. THE BUSINESS - WHAT WE DO

### **Company: PayU India (FinTech)**
- **Parent:** Naspers/Prosus (Global tech investor)
- **Division:** PayU Lending (formerly LazyPay, ZipCredit)
- **Mission:** Enable digital lending for India's underserved SMBs (Small & Medium Businesses)

### **Business Model: Lending-as-a-Service (LaaS)**

We **DON'T** directly lend to customers. Instead:

```
┌─────────────────────────────────────────────────────────────┐
│ PayU Lending = Technology + Risk Platform                   │
│                                                             │
│ We provide:                                                 │
│ • Lending infrastructure (APIs, workflows)                  │
│ • Risk assessment (Credit Bureau, BRE)                      │
│ • Loan management (LMS integration)                         │
│ • Collections & repayment                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Partners = Customer-facing brands                           │
│                                                             │
│ They provide:                                               │
│ • Customer base (GPay, Meesho, PhonePe)                     │
│ • Distribution channel                                      │
│ • Brand trust                                               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ NBFCs/Banks = Capital providers                             │
│                                                             │
│ They provide:                                               │
│ • Loan capital (money to disburse)                          │
│ • NBFC license (RBI regulated)                              │
│ • Risk sharing (co-lending)                                 │
└─────────────────────────────────────────────────────────────┘
```

### **Example Flow: GPay User Gets a Loan**

```
1. User opens GPay app → Sees "Get Instant Loan" offer
   ↓
2. User applies through GPay interface
   ↓
3. GPay calls PayU Lending APIs (our system)
   ↓
4. We process:
   - KYC verification (Aadhaar, PAN)
   - Credit check (CIBIL, Experian)
   - Business Rule Engine (eligibility)
   - Risk assessment (fraud detection)
   ↓
5. If approved, we create loan in LMS (Finflux)
   ↓
6. NBFC (e.g., DMI Finance) disburses money
   ↓
7. Money reaches user's bank account (within minutes)
   ↓
8. User repays through GPay (our collection system)
   ↓
9. Revenue split: PayU (tech fee) + NBFC (interest) + GPay (commission)
```

### **Problem We Solve:**

**For SMBs:**
- ❌ **Before:** Banks don't lend to small businesses (no collateral, paperwork heavy)
- ✅ **After:** Get instant loans (₹10K-₹10L) in 10 minutes via trusted apps (GPay, Meesho)

**For Partners (GPay, Meesho):**
- ❌ **Before:** Need to build lending tech from scratch (1-2 years, ₹10Cr+)
- ✅ **After:** Integrate our APIs (2-3 months, pay-per-transaction)

**For NBFCs:**
- ❌ **Before:** Reach limited (only web/branch), tech outdated
- ✅ **After:** Access millions of customers via partner apps, modern tech stack

---

## 2. THE ARCHITECTURE - HOW WE DO IT

### **High-Level: 3-Tier Microservices**

```
┌─────────────────────────────────────────────────────────────────┐
│                    TIER 1: ORCHESTRATION                        │
│  (Partner-facing APIs - Simplified interface)                   │
│                                                                 │
│  • REST APIs for partners (GPay, Meesho, PhonePe)               │
│  • Request validation & transformation                          │
│  • Webhook management (callbacks to partners)                  │
│  • State management (application_state - deprecated)           │
│                                                                 │
│  Tech: Spring Boot, PostgreSQL, Redis                           │
│  Instances: 3 (load balanced)                                   │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                   TIER 2: ZIPCREDIT (CORE)                      │
│  (Business logic - Lending workflows & risk)                    │
│                                                                 │
│  • Eligibility check (BRE integration)                          │
│  • KYC verification (Aadhaar, PAN, CKYC)                        │
│  • Credit bureau integration (CIBIL, Experian)                  │
│  • Document generation & e-signing (Digio)                      │
│  • NACH mandate (bank account setup)                            │
│  • Loan creation (Finflux LMS)                                  │
│  • State tracker (a_application_stage_tracker)                  │
│                                                                 │
│  Tech: Spring Boot, MySQL (master-slave), Redis, Kafka          │
│  Instances: 5 (auto-scaled)                                     │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                 TIER 3: LOAN REPAYMENT                          │
│  (Post-disbursal - Collections & servicing)                     │
│                                                                 │
│  • EMI collection (auto-debit via NACH)                         │
│  • Payment reminders (SMS, email, WhatsApp)                     │
│  • Late fee calculation & waiver                                │
│  • Foreclosure (early repayment)                                │
│  • Reporting (MIS for partners & NBFCs)                         │
│                                                                 │
│  Tech: Spring Boot, PostgreSQL, Redis, S3                       │
│  Instances: 3 (scheduled jobs)                                  │
└─────────────────────────────────────────────────────────────────┘
```

### **Why 3 Tiers? (Technical + Business Reasons)**

| Tier | Purpose | Why Separate? |
|------|---------|---------------|
| **Orchestration** | Partner interface | • Different partners, different contracts<br>• Faster onboarding (don't touch core)<br>• Webhook complexity isolated |
| **ZipCredit** | Lending logic | • Complex workflows (15-20 steps)<br>• Most changes here (new products)<br>• Needs heavy scaling |
| **Loan Repayment** | Post-disbursal | • Different team (collections focus)<br>• Batch processing (cron jobs)<br>• Different DB (transactional data) |

**Business Example:**
```
When Meesho wants "Credit Line" instead of "Term Loan":
✅ Add new endpoints in Orchestration (2 weeks)
✅ Reuse 80% of ZipCredit logic (eligibility, KYC, etc.)
✅ Loan Repayment unchanged (EMI is EMI)

Without microservices:
❌ Monolith changes = 2 months (risk of breaking existing flows)
```

---

## 3. PRODUCT PORTFOLIO

### **1. Term Loan (Personal/Business)**
- **Amount:** ₹10,000 - ₹10,00,000
- **Tenure:** 3-36 months
- **Use Case:** Working capital, inventory purchase
- **Partners:** GPay, PhonePe, PayU app
- **Volume:** ~15K loans/month

### **2. Credit Line (Revolving)**
- **Amount:** ₹5,000 - ₹5,00,000
- **Tenure:** 12 months (revolving)
- **Use Case:** Flexible borrowing (like credit card)
- **Partners:** Meesho (for sellers)
- **Volume:** ~8K lines/month

### **3. BNPL (Buy Now Pay Later)**
- **Amount:** ₹500 - ₹50,000
- **Tenure:** 0-3 months
- **Use Case:** E-commerce checkout
- **Partners:** Swiggy, Zomato (pilot)
- **Volume:** ~25K transactions/month

### **4. Invoice Discounting**
- **Amount:** ₹50,000 - ₹50,00,000
- **Tenure:** 30-90 days
- **Use Case:** B2B payments (supplier financing)
- **Partners:** Meesho (for vendors)
- **Volume:** ~2K invoices/month

---

## 4. PARTNER ECOSYSTEM

### **Active Partners (15+)**

| Partner | Category | Product | Customer Type | Integration |
|---------|----------|---------|---------------|-------------|
| **GPay** | Payments | Term Loan | Individuals | REST API + Webhooks |
| **Meesho** | E-commerce | Credit Line | SMB Sellers | REST API + Webhooks |
| **PhonePe** | Payments | Term Loan | Individuals | REST API + Webhooks |
| **BharatPe** | Payments | Business Loan | Merchants | REST API + Webhooks |
| **Paytm** | Payments | BNPL | Individuals | REST API |
| **Swiggy** | Food Delivery | BNPL (Pilot) | Customers | REST API |

### **Integration Pattern:**

```
Partner Integration = 3 Components:

1. APIs (Request/Response)
   POST /api/v1/applications/create
   POST /api/v1/applications/{id}/kyc/aadhaar
   GET  /api/v1/applications/{id}/status

2. Webhooks (Async Notifications)
   Partner URL: https://partner.com/webhook/loan-status
   Events: LOAN_APPROVED, LOAN_DISBURSED, EMI_DUE

3. Admin Portal (Self-service)
   • View applications
   • Approve/reject manually
   • Download reports
```

---

## 5. TECHNICAL STACK

### **Backend:**
- **Language:** Java 17
- **Framework:** Spring Boot 3
- **ORM:** Hibernate/JPA
- **Build:** Maven

### **Databases:**
- **Primary:** MySQL 8 (master-slave replication)
- **Secondary:** PostgreSQL 14 (orchestration, loan repayment)
- **Cache:** Redis Cluster (distributed)
- **Message Queue:** Kafka (event streaming)

### **Infrastructure:**
- **Cloud:** AWS (hybrid with on-prem)
- **Compute:** EC2 instances (t3.large)
- **Container:** Docker + Kubernetes (EKS)
- **Load Balancer:** AWS ALB
- **Storage:** S3 (documents, reports)
- **CI/CD:** GitLab CI → Jenkins → Kubernetes

### **Integrations:**
- **LMS:** Finflux (loan management system)
- **KYC:** Digio, NSDL (Aadhaar verification)
- **Credit Bureau:** CIBIL, Experian, CRIF
- **Payment:** NPCI (NACH), RazorPay, PayU gateway
- **Monitoring:** Sentry, Coralogix, Prometheus, Grafana

---

## 6. TEAM STRUCTURE & YOUR ROLE

### **PayU Lending Organization:**

```
PayU Lending India
├── Product Team (20)
│   ├── Product Managers (5)
│   ├── Business Analysts (8)
│   └── QA (7)
├── Engineering Team (45)
│   ├── Backend (You are here) (25)
│   │   ├── ZipCredit Squad (10) ← Your squad
│   │   ├── Orchestration Squad (8)
│   │   └── Loan Repayment Squad (7)
│   ├── Frontend (12)
│   └── DevOps/SRE (8)
├── Data & Analytics (10)
└── Risk & Compliance (15)
```

### **Your Role: Senior Software Engineer - ZipCredit Squad**

**Responsibilities:**
1. ✅ **Feature Development** (60%)
   - New partner integrations (GPay term loan)
   - New product features (auto-disbursal, factory pattern)
   - API enhancements

2. ✅ **Production Support** (25%)
   - Debug production issues (memory leak investigation)
   - Performance optimization (cache implementation)
   - Incident resolution (on-call rotation)

3. ✅ **Code Quality** (15%)
   - Code reviews (SOLID principles, design patterns)
   - Tech debt reduction
   - Documentation

**Key Contributions:**
- 🚀 **1,066 commits** in ZipCredit (high contributor)
- 🏗️ **Auto-disbursal factory pattern** (extensible design)
- 🐛 **GPay cache race condition fix** (concurrency issue)
- 🔧 **SFTP upload failure resolution** (BouncyCastle issue)
- 📊 **20% performance improvement** (caching, read replicas)

---

## 7. BUSINESS METRICS

### **Scale (Monthly):**
- **Applications:** ~50,000/month
- **Loan Disbursal:** ₹250 Crores/month
- **Active Loans:** ~2,00,000
- **Partners:** 15 active integrations
- **Average Loan Size:** ₹50,000

### **Technical Metrics:**
- **API Requests:** 5M+/day
- **Peak TPS:** 500 transactions/sec
- **Latency:** p95 < 200ms (orchestration APIs)
- **Uptime:** 99.95% (SLA)
- **Event Processing:** 50K events/day

### **Business Impact:**
- **Revenue:** Technology fee (0.5-1% of loan amount)
- **Market:** #3 player in SMB digital lending
- **Growth:** 3x YoY (2023 → 2024)

---

## 8. INTERVIEW TALKING POINTS

### **Q: What business is your team doing?**

**Answer:**
> "We're building a **Lending-as-a-Service platform** for India's SMB segment. Think of us as the Stripe of lending.
>
> **The Problem:**
> Small businesses can't get loans from banks (no collateral, paperwork heavy). Banks' processes are designed for large corporates.
>
> **Our Solution:**
> We provide lending infrastructure to consumer apps (GPay, Meesho) so they can offer instant loans to their users. We handle the complex stuff:
> - Credit assessment (CIBIL, fraud detection)
> - Regulatory compliance (KYC, NACH)
> - Loan management (Finflux LMS)
> - Collections & repayment
>
> **Business Model:**
> - Partners get happy customers (sticky feature)
> - NBFCs get distribution (millions of customers)
> - We get technology fees (₹500-2000 per loan)
>
> **My Role:**
> I work on ZipCredit - the core lending engine. I've built features like auto-disbursal, partner integrations, and production debugging (memory leaks, cache race conditions).
>
> **Scale:**
> ~50K applications/month, ₹250Cr disbursal, 5M+ API requests/day."

---

### **Q: Explain your architecture**

**Answer:**
> "We use a **3-tier microservices architecture** with clear separation of concerns:
>
> **Tier 1: Orchestration** (Partner APIs)
> - Partner-facing REST APIs
> - Request transformation (partner format → internal format)
> - Webhook management (async callbacks)
> - **Why separate:** Each partner has different contracts, faster onboarding
>
> **Tier 2: ZipCredit** (Core Lending)
> - Business logic (eligibility, KYC, credit check)
> - State machine (application workflow)
> - External integrations (CIBIL, Digio, Finflux)
> - **Why separate:** Complex workflows, most changes here, needs scaling
>
> **Tier 3: Loan Repayment** (Collections)
> - EMI collection (NACH auto-debit)
> - Reminders & late fees
> - Reporting (MIS)
> - **Why separate:** Different team, batch processing, different DB
>
> **Tech Stack:**
> - Java 17 + Spring Boot 3
> - MySQL (master-slave) + Redis (distributed cache)
> - Kubernetes (EKS) + Jenkins CI/CD
> - Event-driven (Kafka) + Async processing (CompletableFuture)
>
> **Trade-off:**
> - ✅ **Pros:** Fast partner onboarding (2-3 months), independent scaling, fault isolation
> - ❌ **Cons:** Distributed complexity (tracing, debugging), eventual consistency
>
> **Why This Works:**
> - New partner = just Orchestration layer changes (ZipCredit untouched)
> - New product = reuse 80% of ZipCredit logic
> - Collections team independent (no blocking backend team)"

---

### **Q: What's the most challenging technical problem you've solved?**

**Answer:**
> "**GPay cache race condition** causing duplicate loan creation:
>
> **Problem:**
> - 3 EC2 instances (load balanced)
> - Same eligibility request hits multiple instances (retry logic)
> - Both check cache: 'eligibility done?' → NO (race condition)
> - Both call CIBIL API → Both create loans → DUPLICATE!
>
> **Root Cause:**
> - Redis cache had TTL (5 min)
> - Between check and set, other instance inserted
> - Cache was per-instance (not distributed)
>
> **Solution: 3-Layer Defense**
> 1. **Distributed lock (Redisson)** - Only one instance processes
> 2. **Idempotency check (DB)** - Double-check after lock
> 3. **Unique constraint (DB)** - Last resort safety net
>
> **Impact:**
> - Duplicate loans: 0.5% → 0% (eliminated)
> - Saved ₹5L/month in NBFC penalties
> - Improved partner trust
>
> **Learning:**
> - Concurrency is hard in distributed systems
> - Multiple layers of protection > single solution
> - Monitoring is key (detected via Sentry alerts)"

---

### **Q: How do you handle production issues?**

**Answer:**
> "**Systematic approach:**
>
> **Step 1: Triage (5 mins)**
> - Check Sentry alerts (exception details)
> - Check Coralogix logs (error patterns)
> - Check Grafana (CPU, memory, latency spikes)
>
> **Step 2: Impact Assessment**
> - How many applications affected?
> - Which partner? (GPay = P0, smaller partner = P2)
> - Revenue impact?
>
> **Step 3: Quick Mitigation**
> - Rollback recent deployment (if applicable)
> - Restart service (if memory leak)
> - Circuit breaker (if third-party API down)
>
> **Step 4: Root Cause Analysis**
> - SSH to prod log server
> - Grep logs for application_id
> - Check DB state (what's missing?)
> - Check external API responses
>
> **Step 5: Fix & Deploy**
> - Hotfix branch (if code issue)
> - Config change (if threshold issue)
> - Manual data fix (if data corruption)
>
> **Example: Memory Leak**
> - Symptom: EC2 memory 90%+ after 2 days
> - Investigation: Heap dump analysis (MAT tool)
> - Root cause: Unbounded cache (no eviction)
> - Fix: Added LRU eviction (max 10K entries)
> - Prevention: Added memory alerts (80% threshold)"

---

## 🎯 KEY TAKEAWAYS

### **Business:**
- Lending-as-a-Service for SMBs via partner apps (GPay, Meesho)
- ₹250Cr/month disbursal, 50K applications/month
- Technology fee model (₹500-2000 per loan)

### **Architecture:**
- 3-tier microservices (Orchestration → ZipCredit → Loan Repayment)
- Event-driven (Kafka) + Async (CompletableFuture)
- Distributed locks (Redisson) for concurrency

### **Your Impact:**
- 1,066 commits (high contributor)
- Auto-disbursal factory pattern (extensibility)
- Production issue resolution (memory leak, cache race)

### **Tech Stack:**
- Java 17 + Spring Boot 3 + MySQL + Redis + Kafka
- Kubernetes (EKS) + Jenkins + AWS
- 99.95% uptime, p95 < 200ms

---

**Interview Tip:** Focus on **WHY** (business value) and **TRADE-OFFS** (what you gave up), not just **WHAT** (implementation).

Good luck! 🚀
