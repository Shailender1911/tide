# 🏋️ Practice Problems for Tide Code Review Interview

This folder contains **14 practice problems** covering various FinTech scenarios to help you prepare for the Tide Code Review interview.

## 📚 Available Problems

### Beginner Level (Warm-up)
| # | Problem | Difficulty | Time | Issues | Focus Areas |
|---|---------|------------|------|--------|-------------|
| 1 | [User Registration](PRACTICE_PROBLEM_1_EASY.md) | 🟢 Easy | 20 min | 10-12 | Password security, HTTP methods |
| 4 | [Inventory Management](PRACTICE_PROBLEM_4_INVENTORY.md) | 🟡 Medium | 30 min | 15+ | Concurrency, State management |

### Core FinTech (Must Practice!)
| # | Problem | Difficulty | Time | Issues | Focus Areas |
|---|---------|------------|------|--------|-------------|
| 2 | [Payment Processing](PRACTICE_PROBLEM_2_MEDIUM.md) | 🟡 Medium | 30 min | 15-18 | PCI compliance, Transactions |
| 3 | [Money Transfer](PRACTICE_PROBLEM_3_HARD.md) | 🔴 **Hard** | 40 min | **20+** | **⭐ Most similar to Tide interview!** |
| 6 | [Account Opening](PRACTICE_PROBLEM_6_ACCOUNT_OPENING.md) | 🔴 Hard | 40 min | 18+ | Financial integrity, KYC |
| 12 | [Currency Exchange](PRACTICE_PROBLEM_12_FOREX.md) | 🔴 Hard | 40 min | 20+ | FX rates, Rate manipulation |

### Compliance & Security
| # | Problem | Difficulty | Time | Issues | Focus Areas |
|---|---------|------------|------|--------|-------------|
| 5 | [KYC Verification](PRACTICE_PROBLEM_5_KYC.md) | 🟡 Medium | 30 min | 15+ | PII handling, Compliance |
| 13 | [Audit Logging](PRACTICE_PROBLEM_13_AUDIT.md) | 🟡 Medium | 30 min | 15+ | Immutability, Compliance |

### Business Operations
| # | Problem | Difficulty | Time | Issues | Focus Areas |
|---|---------|------------|------|--------|-------------|
| 7 | [Invoice Generation](PRACTICE_PROBLEM_7_INVOICE.md) | 🟡 Medium | 30 min | 15+ | Authorization, Data integrity |
| 8 | [Subscription Billing](PRACTICE_PROBLEM_8_SUBSCRIPTION.md) | 🔴 Hard | 40 min | 20+ | Recurring billing, Prorations |
| 11 | [Report Generation](PRACTICE_PROBLEM_11_REPORT.md) | 🟡 Medium | 30 min | 15+ | Data exposure, Memory |

### Platform Services
| # | Problem | Difficulty | Time | Issues | Focus Areas |
|---|---------|------------|------|--------|-------------|
| 9 | [Document Upload](PRACTICE_PROBLEM_9_FILE_UPLOAD.md) | 🟡 Medium | 30 min | 15+ | Path traversal, File security |
| 10 | [Notification Service](PRACTICE_PROBLEM_10_NOTIFICATION.md) | 🟡 Medium | 30 min | 15+ | PII logging, Preferences |
| 14 | [Rate Limiting](PRACTICE_PROBLEM_14_RATE_LIMIT.md) | 🔴 Hard | 40 min | 18+ | Thread safety, Distributed |

---

## 🎯 Recommended Study Plan

### 3-Day Plan (Before Interview)

**Day 1 - Fundamentals:**
- ✅ Problem 1 (User Registration) - Warm up
- ✅ Problem 4 (Inventory) - State management
- ✅ Review common issues checklist

**Day 2 - Core FinTech:**
- ✅ Problem 2 (Payment) - PCI compliance
- ✅ Problem 3 (Money Transfer) - **CRITICAL - Most like Tide!**
- ✅ Problem 6 (Account Opening) - Banking

**Day 3 - Final Prep:**
- ✅ Problem 12 (Forex) or Problem 8 (Subscription)
- ✅ Review all solutions
- ✅ Practice articulating issues

### Weekend Intensive (2 Days)

**Saturday:**
- Morning: Problems 1, 2, 4
- Afternoon: Problems 3, 6
- Evening: Review solutions, note patterns

**Sunday:**
- Morning: Problems 5, 7, 8
- Afternoon: Problems 9, 10, or choice of others
- Evening: Final review of critical issues

---

## 📋 Universal Issue Checklist

Use this checklist for EVERY problem:

### 🔴 Critical (Always Check First!)
- [ ] **Auth bypass from client?** (`isAdmin`, `skipValidation`, `override`)
- [ ] **double for money?** (Should be BigDecimal)
- [ ] **No @Transactional?** (For multi-step operations)
- [ ] **PII in logs?** (Passwords, card numbers, SSN)
- [ ] **Secrets in URL/params?** (Passwords, API keys, card details)

### 🔴 Major
- [ ] **No response body?** (void return)
- [ ] **Wrong HTTP method?** (PUT/GET for create)
- [ ] **== instead of .equals()?** (Object comparison)
- [ ] **Optional.get() without check?** (NPE risk)
- [ ] **Random ID generation?** (Use UUID)

### 🟡 Medium
- [ ] **Field injection?** (Should be constructor)
- [ ] **InternalServerError for everything?** (Use specific codes)
- [ ] **Static mutable state?** (Thread safety)
- [ ] **No input validation?**
- [ ] **Logger declared but not used?**

### 🟢 Minor
- [ ] **java.util.Date?** (Use Instant)
- [ ] **No pagination?**
- [ ] **Magic numbers?**
- [ ] **Entities returned directly?** (Use DTOs)

---

## 🗣️ How to Articulate Issues

### Template
```
🔴 CRITICAL [Line XX]: [Category] - [One-line description]

Problem: [What's wrong]
Impact: [What could happen]
Fix: [How to fix it]
```

### Example
```
🔴 CRITICAL [Line 27]: Security - Authorization bypass via client parameter

Problem: The `isAdminAgent` boolean comes from the client request.
Impact: Any user can bypass all authorization checks by sending 
        isAdminAgent=true, accessing any account, performing any action.
Fix: Remove the parameter. Check admin role server-side using 
     @PreAuthorize("hasRole('ADMIN')") or SecurityContext.
```

---

## 📊 Quick Stats

| Total Problems | Total Issues | Avg Time |
|----------------|--------------|----------|
| 14 | 200+ | 32 min |

| By Difficulty | Count |
|---------------|-------|
| 🟢 Easy | 1 |
| 🟡 Medium | 8 |
| 🔴 Hard | 5 |

| By Category | Problems |
|-------------|----------|
| Financial/Banking | 3, 6, 8, 12 |
| Security/Auth | 5, 9, 13, 14 |
| General Backend | 1, 4, 7, 10, 11 |
| Payments | 2 |

---

## ✅ Final Checklist Before Interview

- [ ] Completed at least 5 practice problems
- [ ] Can identify 15+ issues in 40 minutes
- [ ] Know the top 5 critical patterns by heart:
  1. Auth bypass from client params
  2. No @Transactional for money
  3. double instead of BigDecimal
  4. == instead of .equals()
  5. No response body (void)
- [ ] Practiced explaining issues out loud
- [ ] Reviewed actual Tide problem in `../docs/`

---

## 🍀 Good Luck!

Remember Tide's focus:
- **Security first** - They're a FinTech
- **Data integrity** - Financial transactions must be correct
- **Code quality** - They value well-written, testable code

You've got this! 💪
