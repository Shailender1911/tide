# 🏋️ Practice Problems for Tide Code Review Interview

This folder contains practice problems to help you prepare for the Tide Code Review interview.

## 📚 Available Problems

| Problem | Difficulty | Time | Focus Areas |
|---------|------------|------|-------------|
| [Problem 1: User Registration](PRACTICE_PROBLEM_1_EASY.md) | 🟢 Easy | 20 min | Security, Password handling, HTTP methods |
| [Problem 2: Payment Processing](PRACTICE_PROBLEM_2_MEDIUM.md) | 🟡 Medium | 30 min | PCI compliance, Transactions, Financial precision |
| [Problem 3: Money Transfer](PRACTICE_PROBLEM_3_HARD.md) | 🔴 Hard | 40 min | **Most similar to actual interview!** |
| [Problem 4: Inventory Management](PRACTICE_PROBLEM_4_INVENTORY.md) | 🟡 Medium | 30 min | Concurrency, State management, Race conditions |

## 🎯 How to Practice

### Step 1: Set Up Environment
- Open a Google Doc (to simulate interview)
- Set a timer for the specified time limit
- Have the problem code in another window

### Step 2: Review Process
1. **First 5 minutes**: Read and understand the code flow
2. **Next 5-10 minutes**: Identify critical security issues
3. **Remaining time**: Add detailed comments for all issues
4. **Last 3-5 minutes**: Review and prioritize

### Step 3: Self-Evaluate
- Check your findings against the solution
- Note what you missed
- Practice explaining issues out loud

## 📋 Common Issue Categories to Look For

### 🔴 Security (Always Check First!)
- [ ] Authorization bypass (isAdmin from client)
- [ ] Input validation missing
- [ ] Sensitive data exposure (passwords, card numbers)
- [ ] Proper authentication checks

### 🔴 Financial/Data Integrity
- [ ] `@Transactional` for multi-step operations
- [ ] `BigDecimal` for money (not double)
- [ ] Proper ID generation (UUID, not Random)
- [ ] Atomic operations

### 🔴 Correctness
- [ ] `!=` vs `.equals()` for objects
- [ ] `>` vs `>=` comparisons
- [ ] Null checks (Optional handling)
- [ ] Response bodies returned

### 🟡 API Design
- [ ] Correct HTTP methods (POST for create, etc.)
- [ ] Proper status codes (400, 401, 403, 404, 422, 500)
- [ ] Response DTOs (not entities)
- [ ] Input validation annotations

### 🟡 Code Quality
- [ ] Constructor injection (not @Autowired fields)
- [ ] Private fields (not public)
- [ ] Logger used properly
- [ ] No System.out.println

### 🟢 Concurrency & Performance
- [ ] Thread-safe collections
- [ ] Race conditions
- [ ] Cache consistency
- [ ] Optimistic locking

## 🗣️ Practice Articulation

For each issue you find, practice explaining it using this pattern:

> **"[SEVERITY]: [CATEGORY] - [Problem Description]"**
> 
> "The issue is... [explain what's wrong]"
> 
> "The impact is... [explain the consequence]"
> 
> "The fix is... [explain how to fix it]"

### Example:
> **"CRITICAL: Security - Authorization bypass via client parameter"**
> 
> "The issue is that `isAdminAgent` comes from the client request as a query parameter. Any user can set this to true."
> 
> "The impact is complete authorization bypass - any user can perform admin actions, access any account, and bypass fraud detection."
> 
> "The fix is to check admin role server-side using Spring Security's `@PreAuthorize("hasRole('ADMIN')")` or by checking the user's roles from the SecurityContext."

## ✅ Checklist Before Interview

- [ ] Completed all 4 practice problems
- [ ] Can identify 15+ issues in 40 minutes
- [ ] Can explain issues clearly (Problem → Impact → Fix)
- [ ] Know the top 10 most common issues by heart
- [ ] Reviewed the actual Tide problem in `../docs/`
- [ ] Tested Google Meet and internet connection

## 🍀 Good Luck!

Remember:
- **Major issues have more weightage** - prioritize security and data integrity
- **Comment as you go** - don't wait until the end
- **Be specific** - explain WHY and HOW to fix
- **Stay calm** - 40 minutes is enough if you're systematic

You've got this! 💪

