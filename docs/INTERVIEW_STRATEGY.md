# Interview Strategy for Tide Code Review

## 🎯 Interview Overview

- **Duration**: 40 mins code review + Q&A
- **Format**: Google Doc with code, add comments like PR review
- **Interviewers**: Engineering peers
- **Tools**: Internet allowed (no AI)

---

## 📋 Pre-Interview Checklist

### Environment Setup
- [ ] Stable internet connection
- [ ] Quiet space
- [ ] Google Meet tested
- [ ] Second monitor (optional, for references)

### Mental Preparation
- [ ] Review this repository
- [ ] Practice with the sample code
- [ ] Review common Java issues
- [ ] Rest well the night before

---

## ⏱️ 40-Minute Strategy

### Phase 1: Read & Understand (5-7 minutes)

**Objective**: Understand what the code is supposed to do

1. Read the class/method documentation
2. Identify the main flow:
   - What API endpoint is this?
   - What parameters does it take?
   - What should it return?
3. Note any immediate red flags

**Don't**: Start commenting immediately  
**Do**: Build mental model of the code

### Phase 2: Security Scan (5-7 minutes)

**Objective**: Find critical security issues first

Checklist:
- [ ] Authorization - Is access control proper?
- [ ] Input validation - Are inputs validated?
- [ ] Data exposure - Is sensitive data protected?
- [ ] Injection risks - SQL, command injection?

**Why First**: Security issues are highest priority and often most impactful

### Phase 3: Deep Review (20-25 minutes)

**Objective**: Systematic review of all issues

Work through the code:
1. Method signature & annotations
2. Input handling
3. Business logic
4. Data operations
5. Response handling
6. Error handling

**Comment as you go!** Don't wait until the end.

### Phase 4: Polish & Prioritize (3-5 minutes)

**Objective**: Ensure quality of review

1. Re-read your comments
2. Add severity labels (CRITICAL, MAJOR, MINOR)
3. Check you haven't missed obvious issues
4. Add any final thoughts

---

## 🏷️ Comment Labeling System

Use consistent prefixes:

```
🔴 CRITICAL: [Security] isAdminAgent from client input allows auth bypass.
   Fix: Check roles server-side via Spring Security @PreAuthorize

🔴 MAJOR: [Financial] Using double for money causes precision errors.
   Fix: Use BigDecimal for all monetary calculations.

🟡 MEDIUM: [REST] PUT should be POST for creating new resources.

🟢 MINOR: Logger declared but never used. Add audit logging.

❓ QUESTION: What happens if credit limit check fails? Should return error?
```

---

## 💡 Key Points to Remember

### Financial Code Specific
1. **Always use BigDecimal for money**
2. **Always use @Transactional for money transfers**
3. **Always generate unique IDs properly (UUID)**
4. **Always log financial transactions**
5. **Always return transaction confirmations**

### Spring Boot Specific
1. Constructor injection > Field injection
2. Use @Valid for input validation
3. Use ResponseEntity for control over status codes
4. Use specific exceptions with @ResponseStatus

### Security Specific
1. Never trust client input for authorization
2. Use server-side role checking
3. Validate all inputs
4. Use proper status codes (403, 401, 400)

---

## 🗣️ How to Articulate Issues

### Pattern: Problem → Impact → Solution

**Example 1 - Security**:
> "The `isAdminAgent` parameter comes directly from the client request. This means any user can bypass authorization by sending `isAdminAgent=true`. This is a critical security vulnerability known as Broken Access Control. The fix is to determine admin status server-side by checking the user's roles from the Spring Security context."

**Example 2 - Financial**:
> "Using `double` for the loan amount will cause precision errors due to how floating-point arithmetic works in computers. For example, `0.1 + 0.2` doesn't equal `0.3` in floating-point. For financial applications, we must use `BigDecimal` to ensure accurate calculations."

**Example 3 - Transactions**:
> "The debit, credit, and loan registration operations are not wrapped in a transaction. If the credit fails after the debit succeeds, money will disappear from the system. We need `@Transactional` to ensure atomicity - either all operations succeed, or all are rolled back."

---

## 🙋 Potential Follow-up Questions

Be prepared to discuss:

1. **How would you handle the external LMS failing?**
   - Saga pattern, compensating transactions
   - Retry with backoff
   - Circuit breaker

2. **How would you make this idempotent?**
   - Idempotency keys in headers
   - Check for duplicate requests before processing

3. **How would you handle concurrency?**
   - Optimistic locking (version field)
   - Pessimistic locking (SELECT FOR UPDATE)
   - Database constraints

4. **How would you test this?**
   - Unit tests with mocked services
   - Integration tests with test database
   - Security tests for authorization bypass

5. **How would you log this for audit?**
   - Structured logging with MDC
   - Log before and after operations
   - Include transaction IDs for correlation

---

## ✅ Final Checklist

Before saying you're done:

- [ ] Found the security issue (isAdminAgent)?
- [ ] Found the transaction issue?
- [ ] Found the BigDecimal issue?
- [ ] Found the response body issue?
- [ ] Found the comparison operator issues?
- [ ] Mentioned HTTP method (PUT vs POST)?
- [ ] Mentioned error handling issues?
- [ ] Comments are clear and actionable?
- [ ] Severity labels added?

---

## 🍀 Good Luck!

Remember:
- **Stay calm** - You have 40 minutes, use them wisely
- **Be thorough** - Major issues have more weightage
- **Be constructive** - Suggest fixes, not just problems
- **Ask questions** - It shows engagement

You've got this! 💪

