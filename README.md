# Tide Interview Preparation - Complete Guide

> **Position**: Senior Backend Engineer (Java)  
> **Company**: [Tide](https://www.tide.co)

## 📋 Overview

This repository contains comprehensive preparation materials for Tide's interview process:
1. **Code Review Round** (~40 mins) - ✅ Completed
2. **Hiring Manager Round** (~45-60 mins) - 📝 Current Focus

### Code Review Round
The interview involves reviewing a piece of Java code (~50 lines) written by a "junior developer" with multiple issues to identify.

### Hiring Manager Round
Technical conversation about engineering fundamentals, project discussions, system design, and behavioral questions.

## 🎯 Interview Format

- **Duration**: 40 minutes for code review + Q&A
- **Format**: Review code in a Google Doc, adding comments like a PR review
- **Tools Allowed**: Internet for reference (no AI tools)
- **Focus Areas**: 
  - Security vulnerabilities
  - Code correctness
  - Best practices
  - API design
  - Financial transaction handling

## 📁 Repository Structure

```
tide-interview-prep/
├── README.md                          # This file
├── docs/
│   ├── PROBLEM_CODE.md               # Original problematic code
│   ├── BUGS_IDENTIFIED.md            # Comprehensive bug analysis
│   ├── CODE_REVIEW_TIPS.md           # General code review best practices
│   ├── INTERVIEW_STRATEGY.md         # Strategy for code review round
│   ├── TRANSACTIONS_DEEP_DIVE.md     # Transaction concepts explained
│   │
│   ├── HIRING_MANAGER_PREP.md        # 🆕 Complete Hiring Manager guide
│   ├── PROJECT_DISCUSSION_FRAMEWORK.md # 🆕 STAR method for projects
│   ├── TECHNICAL_DEEP_DIVE.md         # 🆕 System design, security, etc.
│   ├── BEHAVIORAL_QUESTIONS.md        # 🆕 Behavioral Q&A prep
│   └── QUESTIONS_TO_ASK.md            # 🆕 Questions for hiring manager
│
├── prep-manager-round/                # 🆕 Hiring Manager Round preparation
│   ├── README.md                      # Study plan and overview
│   ├── TIDE_LOAN_REPAYMENT_DEEP_DIVE.md
│   ├── TIDE_LOAN_REPAYMENT_QUICK_REF.md
│   ├── TIDE_INTERVIEW_STRATEGY.md
│   ├── TIDE_GENERAL_INTERVIEW_TOPICS.md
│   └── TIDE_*.md                      # Additional preparation materials
│
├── practice/                          # Code review practice problems
│   ├── README.md
│   └── PRACTICE_PROBLEM_*.md          # 14 practice problems with solutions
│
└── src/
    └── main/java/vnd/credit/loans/
        ├── controller/
        │   └── LoanController.java   # Fixed controller
        ├── dto/
        │   ├── LoanRequest.java       # Request DTO
        │   └── LoanResponse.java      # Response DTO
        ├── exception/
        │   ├── AccountNotFoundException.java
        │   ├── CreditLimitExceededException.java
        │   ├── InsufficientBalanceException.java
        │   ├── GlobalExceptionHandler.java
        │   └── UnauthorizedAccessException.java
        ├── model/
        │   └── Account.java           # Account entity
        └── service/
            ├── AccountService.java
            └── LoanManagementService.java
```

## 🔴 Key Issues Summary

| Priority | Issue | Category |
|----------|-------|----------|
| 🔴 CRITICAL | `isAdminAgent` from client request | Security (IDOR) |
| 🔴 CRITICAL | No `@Transactional` for money transfer | Data Integrity |
| 🔴 MAJOR | `void` return type - no response | API Design |
| 🔴 MAJOR | `double` for money amounts | Financial Precision |
| 🔴 MAJOR | `!=` instead of `.equals()` | Logic Bug |
| 🔴 MAJOR | Random loan ID generation | Uniqueness |
| 🟡 MEDIUM | Wrong HTTP method (PUT vs POST) | REST Semantics |
| 🟡 MEDIUM | `InternalServerError` for all errors | Error Handling |
| 🟡 MEDIUM | Field injection, public fields | Code Quality |
| 🟢 MINOR | Unused logger | Observability |
| 🟢 MINOR | Poor URL design | API Design |

## 📚 Quick Links

### Code Review Round (Completed ✅)
- [Original Problematic Code](docs/PROBLEM_CODE.md)
- [Detailed Bug Analysis](docs/BUGS_IDENTIFIED.md)
- [Code Review Best Practices](docs/CODE_REVIEW_TIPS.md)
- [Interview Strategy](docs/INTERVIEW_STRATEGY.md)
- [Practice Problems](practice/README.md) - 14 problems with solutions

### Hiring Manager Round (Current Focus 📝)
- **[📁 Complete Preparation Materials](prep-manager-round/)** - **Start here!** 🆕
  - [Loan Repayment Deep Dive](prep-manager-round/TIDE_LOAN_REPAYMENT_DEEP_DIVE.md) - Primary project guide
  - [Quick Reference Card](prep-manager-round/TIDE_LOAN_REPAYMENT_QUICK_REF.md) - Last-minute revision
  - [Interview Strategy](prep-manager-round/TIDE_INTERVIEW_STRATEGY.md) - Presentation framework
  - [General Topics](prep-manager-round/TIDE_GENERAL_INTERVIEW_TOPICS.md) - System design, transactions, security
- [Complete Preparation Guide](docs/HIRING_MANAGER_PREP.md) - Alternative guide
- [Project Discussion Framework](docs/PROJECT_DISCUSSION_FRAMEWORK.md) - STAR method
- [Technical Deep-Dive](docs/TECHNICAL_DEEP_DIVE.md) - System design, security, transactions
- [Behavioral Questions](docs/BEHAVIORAL_QUESTIONS.md) - Common Q&A
- [Questions to Ask](docs/QUESTIONS_TO_ASK.md) - Thoughtful questions
- [Transactions Deep-Dive](docs/TRANSACTIONS_DEEP_DIVE.md) - Detailed transaction concepts

## 🛠️ Resources

- [Google's Code Review Guidelines](https://google.github.io/eng-practices/review/)
- [Stack Overflow: How to do Code Reviews](https://stackoverflow.com/questions/tagged/code-review)
- [OWASP Top 10 Security Risks](https://owasp.org/www-project-top-ten/)

## 📝 Interview Tips

### Code Review Round (Completed ✅)
1. **Read the code carefully** - Take 5-10 mins to understand the flow
2. **Prioritize critical issues** - Security and data integrity first
3. **Comment as you go** - Don't wait until the end
4. **Be specific** - Explain WHY something is wrong and HOW to fix it
5. **Ask clarifying questions** - Shows engagement

### Hiring Manager Round (Current Focus 📝)
1. **Prepare Your Projects** - Use STAR method, focus on YOUR contributions
2. **Technical Deep-Dive** - Review system design, security, transactions, concurrency
3. **Practice Behavioral Questions** - Prepare 5-6 STAR stories
4. **Prepare Questions** - Show genuine interest, assess fit
5. **Be Authentic** - Don't memorize, speak naturally about your experience

---

## 🎯 Hiring Manager Round - Quick Start

**Priority Reading Order** (New Materials):
1. **[prep-manager-round/README.md](prep-manager-round/README.md)** - Complete overview & study plan 🆕
2. [Loan Repayment Deep Dive](prep-manager-round/TIDE_LOAN_REPAYMENT_DEEP_DIVE.md) - Primary project focus 🆕
3. [General Topics Guide](prep-manager-round/TIDE_GENERAL_INTERVIEW_TOPICS.md) - System design, transactions, security 🆕
4. [Interview Strategy](prep-manager-round/TIDE_INTERVIEW_STRATEGY.md) - Presentation framework 🆕
5. [Quick Reference](prep-manager-round/TIDE_LOAN_REPAYMENT_QUICK_REF.md) - Last-minute revision 🆕

**Alternative Materials** (in docs folder):
1. [HIRING_MANAGER_PREP.md](docs/HIRING_MANAGER_PREP.md) - Complete overview
2. [PROJECT_DISCUSSION_FRAMEWORK.md](docs/PROJECT_DISCUSSION_FRAMEWORK.md) - Structure your project stories
3. [TECHNICAL_DEEP_DIVE.md](docs/TECHNICAL_DEEP_DIVE.md) - Brush up on technical concepts
4. [BEHAVIORAL_QUESTIONS.md](docs/BEHAVIORAL_QUESTIONS.md) - Prepare STAR stories
5. [QUESTIONS_TO_ASK.md](docs/QUESTIONS_TO_ASK.md) - Prepare thoughtful questions

**Key Topics to Master**:
- ✅ System Design & Architecture (scalability, microservices, caching)
- ✅ Security (authentication, API security, financial security)
- ✅ Transactions (ACID, isolation levels, distributed transactions, SAGA)
- ✅ Idempotency (implementation strategies, use cases)
- ✅ Concurrency (race conditions, locking, distributed concurrency)
- ✅ Project Discussion (STAR method, technical challenges, impact)

---

*Good luck with your Hiring Manager round! 🚀*

