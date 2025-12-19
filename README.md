# Tide Code Review Interview Preparation

> **Position**: Senior Backend Engineer (Java)  
> **Interview Type**: Code Review Round (~40 mins)  
> **Company**: [Tide](https://www.tide.co)

## 📋 Overview

This repository contains comprehensive preparation materials for Tide's Code Review interview round. The interview involves reviewing a piece of Java code (~50 lines) written by a "junior developer" with multiple issues to identify.

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
│   └── INTERVIEW_STRATEGY.md         # Strategy for the interview
├── src/
│   └── main/java/vnd/credit/loans/
│       ├── controller/
│       │   └── LoanController.java   # Fixed controller
│       ├── dto/
│       │   ├── LoanRequest.java      # Request DTO
│       │   └── LoanResponse.java     # Response DTO
│       ├── exception/
│       │   ├── AccountNotFoundException.java
│       │   ├── CreditLimitExceededException.java
│       │   ├── InsufficientBalanceException.java
│       │   ├── GlobalExceptionHandler.java
│       │   └── UnauthorizedAccessException.java
│       ├── model/
│       │   └── Account.java          # Account entity
│       └── service/
│           ├── AccountService.java
│           └── LoanManagementService.java
└── pom.xml                           # Maven dependencies
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

- [Original Problematic Code](docs/PROBLEM_CODE.md)
- [Detailed Bug Analysis](docs/BUGS_IDENTIFIED.md)
- [Code Review Best Practices](docs/CODE_REVIEW_TIPS.md)
- [Interview Strategy](docs/INTERVIEW_STRATEGY.md)

## 🛠️ Resources

- [Google's Code Review Guidelines](https://google.github.io/eng-practices/review/)
- [Stack Overflow: How to do Code Reviews](https://stackoverflow.com/questions/tagged/code-review)
- [OWASP Top 10 Security Risks](https://owasp.org/www-project-top-ten/)

## 📝 Interview Tips

1. **Read the code carefully** - Take 5-10 mins to understand the flow
2. **Prioritize critical issues** - Security and data integrity first
3. **Comment as you go** - Don't wait until the end
4. **Be specific** - Explain WHY something is wrong and HOW to fix it
5. **Ask clarifying questions** - Shows engagement

---

*Good luck with your interview! 🚀*

