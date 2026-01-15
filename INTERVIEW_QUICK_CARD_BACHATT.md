# 🃏 BACHATT INTERVIEW - QUICK GLANCE CARD
## Print this or keep open during prep

---

## ⏱️ YOUR 60-SEC INTRO (Practice 3x)
> "I'm Shailender, Senior Software Engineer with **5+ years backend** at PayU Lending.  
> I've **integrated 5 major partners** (GPay, PhonePe, BharatPe, Paytm, Swiggy).  
> Built **NACH & Insurance microservices from scratch**.  
> **20% latency reduction** with Redis, **30% faster onboarding** with state machines.  
> Bachatt's daily savings + UPI autopay aligns with my NACH mandate experience."

---

## 🎯 YOUR 5 KEY ACHIEVEMENTS (Memorize)

| # | Achievement | Metric |
|---|-------------|--------|
| 1 | Partner Integrations | 5 partners (GPay, PhonePe, BharatPe, Paytm, Swiggy) |
| 2 | NACH Service | Built from scratch, 3 NACH types (UPI, API, Physical) |
| 3 | Redis Caching | 20% API latency reduction |
| 4 | State Machine | 30% onboarding time reduction |
| 5 | InsureX Service | Multi-vendor (ICICI, Acko), Factory pattern |

---

## 🔧 PROJECT DEEP-DIVE (Pick 1)

### Option A: DLS NACH Service ⭐ RECOMMENDED
```
Problem: Needed standalone UPI mandate service
Actions:
  ✓ Strategy/Factory pattern for UPI/API/Physical
  ✓ Digio API integration
  ✓ HMAC-SHA256 webhook security
  ✓ State machine for loan workflow
  ✓ Multi-tenant data backfilling
Result: 100% mandate operations, zero unauthorized callbacks
```

### Option B: InsureX Service
```
Problem: Insurance for loans, multiple vendors
Actions:
  ✓ Factory pattern for ICICI/Acko vendors
  ✓ Two-phase flow (Policy → COI)
  ✓ Cron-based retry for failures
  ✓ S3 document storage
Result: Vendor-agnostic architecture, easy to add new vendors
```

---

## 💬 TOP 5 BEHAVIORAL Q&As

### Q1: "Project you owned end-to-end?"
> **NACH Service**: Designed, built, tested, deployed, monitored.  
> Strategy pattern, Digio integration, state machine, webhooks.

### Q2: "Performance improvement?"
> **Redis caching**: Identified cacheable calls, implemented Redisson,  
> NoOp fallback for reliability. **20% latency reduction**.

### Q3: "Disagreement with stakeholders?"
> **IFSC validation**: Product wanted to skip, I showed 15% failure data,  
> proposed cached validation. **Win-win: faster + reliable**.

### Q4: "Failure and learning?"
> **InsureX rate limits**: Didn't check vendor limits, got throttled.  
> **Learned**: Always check SLAs, implemented exponential backoff.

### Q5: "Staying current?"
> **AI tools** (Cursor, ChatGPT), built **ConfigNexus MCP** for AI-config,  
> GATE qualified, engineering blogs.

---

## 📚 FINTECH VOCABULARY (Must Know)

### NACH Terms
| Term | Meaning |
|------|---------|
| **NACH** | National Automated Clearing House - EMI auto-debit system |
| **UMRN** | Unique Mandate Reference Number |
| **eNACH** | Electronic NACH (instant) |
| **UPI Mandate** | UPI-based recurring payment (Bachatt uses this!) |

### Loan Lifecycle
```
APPLICATION → KYC → APPROVAL → DISBURSEMENT → REPAYMENT → CLOSURE
```

### Key Loan Statuses
- `CREATED` → `APPLIED` → `APPROVED` → `DISBURSED` → `CLOSED`
- Failed states: `REJECTED`, `DISBURSEMENT_FAILED`, `REPAYMENT_FAILED`

### KYC Types
- **eKYC**: Aadhaar OTP (2 min)
- **Video KYC**: Live verification (RBI compliant)
- **CKYC**: Central KYC Registry

### Bureau Score
- 750+: Auto-approve
- 650-749: Manual review
- <550: Usually reject

---

## 🎯 BACHATT CONTEXT (Know This!)

| Fact | Value |
|------|-------|
| Users | 10 Lakh+ |
| Total Invested | ₹50+ Crore |
| SEBI ARN | 321640 |
| Min SIP | ₹51/day |
| Partners | SBI, ICICI, Axis, HDFC, Aditya Birla AMCs |
| Key Tech | UPI Autopay, Daily SIP, 2-min KYC |

### Why Bachatt Aligns with Your Experience
1. **UPI Mandate** → You built NACH service
2. **Daily transactions** → You know scale & reliability
3. **KYC compliance** → You integrated video KYC (Hyperverge)
4. **Startup ownership** → You built services end-to-end

---

## ❓ YOUR QUESTIONS TO ASK (Pick 2-3)

1. "What's your biggest **infra challenge** today?"
2. "How do you handle **UPI mandate failures** at scale?"
3. "What's the **90-day roadmap** for engineering?"
4. "How do you measure **impact for engineers**?"
5. "What does **ownership** look like here?"

---

## 🏁 CLOSING LINE
> "Thank you for the time. I'm excited about building reliable savings infrastructure at Bachatt. I enjoy ownership and shipping customer-visible outcomes."

---

## ✅ LAST 10-MIN CHECKLIST

- [ ] Water bottle ready
- [ ] Quiet space, good lighting
- [ ] Resume in front
- [ ] This card open
- [ ] 60-sec intro practiced
- [ ] 2-3 questions ready
- [ ] Deep breath, confident posture

---

**Interview: 6 PM Today | 45 Minutes | Founder Round**

**YOU'VE GOT THIS! 💪**
