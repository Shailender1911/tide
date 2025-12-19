# Code Review Best Practices

This guide covers general code review best practices that will help you in the Tide interview and beyond.

## 🎯 What to Look For

### 1. Security Issues (Highest Priority)

- **Authentication & Authorization**
  - Is authorization checked properly?
  - Are admin functions protected?
  - Is data from client trusted inappropriately?

- **Input Validation**
  - Are all inputs validated?
  - Is there protection against injection attacks?
  - Are boundaries checked (min/max values)?

- **Sensitive Data**
  - Is sensitive data logged?
  - Are secrets hardcoded?
  - Is PII handled correctly?

### 2. Correctness & Logic

- **Business Logic**
  - Does the code do what it's supposed to?
  - Are edge cases handled?
  - Are comparisons correct (==, !=, <, <=, >, >=)?

- **Null Safety**
  - Are null checks present where needed?
  - Is Optional used correctly?
  - Are NPE risks addressed?

- **Data Types**
  - Are appropriate data types used?
  - Is precision maintained for financial calculations?
  - Are there overflow risks?

### 3. API Design

- **HTTP Methods**
  - GET for reading
  - POST for creating
  - PUT for updating (idempotent)
  - PATCH for partial updates
  - DELETE for removing

- **Status Codes**
  - 200 OK - Success
  - 201 Created - Resource created
  - 400 Bad Request - Invalid input
  - 401 Unauthorized - Not authenticated
  - 403 Forbidden - Not authorized
  - 404 Not Found - Resource doesn't exist
  - 422 Unprocessable Entity - Business rule violation
  - 500 Internal Server Error - Server bug

- **Response Bodies**
  - Always return meaningful responses
  - Include relevant data (IDs, timestamps, status)
  - Use consistent response structure

### 4. Transaction Management

For financial applications:
- Use `@Transactional` for atomic operations
- Consider distributed transaction patterns (Saga, Outbox)
- Implement idempotency for retries

### 5. Code Quality

- **Dependency Injection**
  - Prefer constructor injection
  - Use private final fields
  - Avoid field injection with @Autowired

- **Error Handling**
  - Use specific exception types
  - Return appropriate HTTP status codes
  - Include meaningful error messages

- **Logging**
  - Log important operations
  - Use appropriate log levels
  - Don't log sensitive data

---

## 📝 How to Comment in Code Reviews

### Be Specific
❌ "This is wrong"  
✅ "Using `!=` compares object references, not values. For String comparison, use `.equals()` method."

### Explain the Impact
❌ "Use BigDecimal"  
✅ "Using `double` for money causes precision errors (0.1 + 0.2 ≠ 0.3). This could lead to incorrect financial calculations. Use `BigDecimal` instead."

### Provide Solutions
❌ "This is a security issue"  
✅ "Security: `isAdminAgent` comes from client input - anyone can bypass auth by sending `true`. Fix: Check roles from SecurityContext server-side."

### Reference Standards
✅ "This violates REST semantics - POST should be used for creating new resources, not PUT. See: https://restfulapi.net/http-methods/"

### Use Prefixes for Clarity
- **CRITICAL**: Must fix before merge
- **MAJOR**: Should fix, significant impact
- **MINOR**: Nice to have, low impact
- **NIT**: Nitpick, stylistic preference
- **QUESTION**: Seeking clarification

---

## ⏱️ Time Management (40 minutes)

| Time | Activity |
|------|----------|
| 0-5 min | Read code, understand the flow |
| 5-10 min | Identify the most critical issues |
| 10-35 min | Add detailed comments |
| 35-40 min | Review, prioritize, add any missed items |

### Pro Tips

1. **Comment as you go** - Don't wait until the end
2. **Start with security** - Highest impact
3. **Don't get stuck** - If unsure, note it and move on
4. **Ask questions** - Shows engagement and clarifies intent
5. **Be constructive** - Suggest solutions, not just problems

---

## 🔗 Useful References

### Security
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP Cheat Sheets](https://cheatsheetseries.owasp.org/)

### Java Best Practices
- [Effective Java Summary](https://github.com/HugoMatilla/Effective-JAVA-Summary)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

### Spring Framework
- [Spring @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Security](https://docs.spring.io/spring-security/reference/)
- [Spring Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)

### REST API Design
- [REST API Tutorial](https://restfulapi.net/)
- [HTTP Status Codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)

### Code Review
- [Google's Code Review Guidelines](https://google.github.io/eng-practices/review/)
- [How to Do Code Reviews Like a Human](https://mtlynch.io/human-code-reviews-1/)

