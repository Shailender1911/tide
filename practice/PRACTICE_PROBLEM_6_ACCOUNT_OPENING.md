# Practice Problem 6: Account Opening API (Hard)

## Context
A junior developer has written an account opening API for a digital bank. This handles new account creation with initial deposit. Review this code and identify all issues.

**Time Limit**: 40 minutes  
**Difficulty**: Hard  
**Expected Issues**: 18+

---

## Code to Review

```java
package com.bank.accounts;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Account opening controller for new customers.
 * Supports instant account creation with initial deposit.
 * Added referral bonus feature for marketing campaign!
 */
@RequestMapping("/v1/accounts")
@RestController
public class AccountOpeningController {

    @Autowired
    public AccountRepository accountRepository;

    @Autowired
    public CustomerRepository customerRepository;

    @Autowired
    public PaymentGateway paymentGateway;

    @Autowired
    public BonusService bonusService;

    public static final Logger logger = LoggerFactory.getLogger(AccountOpeningController.class);

    // Track referral codes used today
    private static Map<String, Integer> referralUsageToday = new HashMap<>();

    @PostMapping("/open")
    public void openAccount(@RequestParam String customerId,
                           @RequestParam String accountType,
                           @RequestParam double initialDeposit,
                           @RequestParam String cardNumber,
                           @RequestParam String cvv,
                           @RequestParam String referralCode,
                           @RequestParam boolean waiveFees) {

        Customer customer = customerRepository.findById(customerId).get();

        // Check KYC status
        if (customer.getKycStatus() != "VERIFIED") {
            throw new InternalServerError();
        }

        // Create account
        Account account = new Account();
        account.setId(System.currentTimeMillis() + "-" + new Random().nextInt(1000));
        account.setCustomerId(customerId);
        account.setType(accountType);
        account.setBalance(0.0);
        account.setStatus("ACTIVE");
        account.setCreatedAt(new Date());
        account.setInterestRate(getInterestRate(accountType));
        accountRepository.save(account);

        // Process initial deposit
        if (initialDeposit > 0) {
            PaymentResult result = paymentGateway.charge(cardNumber, cvv, initialDeposit);
            if (result.isSuccess()) {
                account.setBalance(initialDeposit);
                accountRepository.save(account);
            }
        }

        // Apply referral bonus
        if (referralCode != null && !referralCode.isEmpty()) {
            referralUsageToday.put(referralCode, 
                referralUsageToday.getOrDefault(referralCode, 0) + 1);
            
            if (referralUsageToday.get(referralCode) <= 10) {
                account.setBalance(account.getBalance() + 50.0);
                accountRepository.save(account);
                bonusService.creditReferrer(referralCode, 25.0);
            }
        }

        // Waive account opening fee for promotions
        if (!waiveFees) {
            account.setBalance(account.getBalance() - 10.0);
            accountRepository.save(account);
        }

        logger.info("Account opened: " + account.getId() + " Card: " + cardNumber);
    }

    @GetMapping("/interest-rate")
    public double getInterestRate(@RequestParam String accountType) {
        switch (accountType) {
            case "SAVINGS": return 0.04;
            case "CURRENT": return 0.01;
            case "BUSINESS": return 0.025;
            default: return 0.02;
        }
    }

    @PostMapping("/close/{accountId}")
    public void closeAccount(@PathVariable String accountId,
                            @RequestParam boolean forceClose) {

        Account account = accountRepository.findById(accountId).get();
        
        if (forceClose || account.getBalance() == 0) {
            account.setStatus("CLOSED");
            accountRepository.save(account);
        }
    }

    @PutMapping("/upgrade/{accountId}")
    public void upgradeAccount(@PathVariable String accountId,
                              @RequestParam String newType,
                              @RequestParam boolean skipEligibility) {

        Account account = accountRepository.findById(accountId).get();

        if (!skipEligibility) {
            if (account.getBalance() < 10000) {
                throw new RuntimeException("Not eligible");
            }
        }

        account.setType(newType);
        account.setInterestRate(getInterestRate(newType));
        accountRepository.save(account);
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Financial integrity, Security, Authorization bypass, Money handling
3. Consider banking regulations and audit requirements

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Multiple authorization bypass parameters (waiveFees, forceClose, skipEligibility)
- Card details handling (PCI DSS)
- Transaction atomicity (what if payment fails after account created?)
- Money precision (double)
- Account ID generation
- Balance can go negative
- Static state for referral tracking
- String comparison with !=
- Multiple saves without transaction
- No ownership verification for account operations

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Security Issues

1. **waiveFees from client** (Lines 39, 76)
   ```java
   @RequestParam boolean waiveFees
   if (!waiveFees) { account.setBalance(account.getBalance() - 10.0); }
   // Anyone can waive fees by setting waiveFees=true
   // Fix: Server-side promotion code validation or role check
   ```

2. **forceClose from client** (Lines 93, 96)
   ```java
   @RequestParam boolean forceClose
   if (forceClose || account.getBalance() == 0)
   // Anyone can close any account by setting forceClose=true
   // Also: No ownership check - User A can close User B's account!
   ```

3. **skipEligibility from client** (Lines 104, 107)
   ```java
   @RequestParam boolean skipEligibility
   if (!skipEligibility) { ... }
   // Bypasses upgrade eligibility checks
   ```

4. **Card details in request params** (Lines 36-37)
   ```java
   @RequestParam String cardNumber,
   @RequestParam String cvv,
   // PCI DSS violation! Card data in URL gets logged
   // Fix: Use tokenized payment, request body over HTTPS
   ```

5. **Card number logged** (Line 81)
   ```java
   logger.info("... Card: " + cardNumber);
   // PCI DSS violation - never log card numbers!
   ```

### 🔴 CRITICAL Financial Issues

6. **double for money** (Lines 35, 51, 61, 68, 72, 78)
   ```java
   double initialDeposit
   account.setBalance(0.0)
   account.setBalance(account.getBalance() + 50.0)
   // Precision errors in financial calculations
   // Fix: Use BigDecimal
   ```

7. **No transaction management** (Lines 48-80)
   ```java
   accountRepository.save(account);  // Account created
   paymentGateway.charge(...);       // What if this fails?
   accountRepository.save(account);  // Balance updated
   // Account exists but no money deposited if payment fails
   // Multiple saves without @Transactional
   ```

8. **Balance can go negative** (Lines 76-79)
   ```java
   if (!waiveFees) {
       account.setBalance(account.getBalance() - 10.0);
   }
   // If initial deposit was 5.0, balance becomes -5.0
   // No check for sufficient balance before fee deduction
   ```

9. **Account ID predictable** (Line 48)
   ```java
   account.setId(System.currentTimeMillis() + "-" + new Random().nextInt(1000));
   // Predictable, collision possible, not suitable for financial system
   // Fix: UUID
   ```

### 🔴 MAJOR Issues

10. **Wrong string comparison** (Line 43)
    ```java
    if (customer.getKycStatus() != "VERIFIED")
    // Uses != instead of !equals() - compares references!
    // Will likely always throw error even for verified customers
    ```

11. **No response body** (All endpoints)
    ```java
    public void openAccount(...)
    // Client gets no account number, confirmation, nothing
    ```

12. **Static state for referrals** (Line 29)
    ```java
    private static Map<String, Integer> referralUsageToday = new HashMap<>();
    // Not thread-safe (HashMap)
    // Lost on restart
    // "Today" never resets
    // Won't work in distributed system
    ```

13. **Optional.get() without check** (Lines 41, 95, 106)
    ```java
    customerRepository.findById(customerId).get()
    // NPE if not found
    ```

14. **No ownership verification** (Lines 92-99, 101-115)
    ```java
    @PostMapping("/close/{accountId}")
    // Anyone can close anyone's account
    // No check if current user owns this account
    ```

15. **Referral bonus not transactional** (Lines 64-73)
    ```java
    account.setBalance(account.getBalance() + 50.0);
    accountRepository.save(account);
    bonusService.creditReferrer(referralCode, 25.0);
    // If creditReferrer fails, account got bonus but referrer didn't
    ```

### 🟡 MEDIUM Issues

16. **Field injection** (Lines 15-25)
    ```java
    @Autowired public AccountRepository
    ```

17. **InternalServerError for KYC failure** (Line 44)
    ```java
    throw new InternalServerError();
    // Should be 400/403 with clear message
    ```

18. **RuntimeException for eligibility** (Line 109)
    ```java
    throw new RuntimeException("Not eligible");
    // Should be proper exception with 400 status
    ```

19. **No input validation**
    - accountType not validated against allowed types
    - initialDeposit could be negative
    - newType could be invalid

20. **Balance comparison with ==** (Line 96)
    ```java
    if (forceClose || account.getBalance() == 0)
    // For double, should use comparison with tolerance
    // For BigDecimal, use compareTo
    ```

21. **java.util.Date** (Line 53)
    ```java
    new Date()
    ```

### 🟢 MINOR Issues

22. **Public method for interest rate** (Lines 84-91)
    ```java
    public double getInterestRate(@RequestParam String accountType)
    // This is exposed as an endpoint but also called internally
    // Should be private helper + separate endpoint
    ```

23. **No audit logging**
    - Account opening, closing, upgrade should be audited
    - Fee waivers should be logged

24. **Magic numbers** (Lines 68, 72, 78, 108)
    ```java
    50.0, 25.0, 10.0, 10000
    // Should be configuration constants
    ```

</details>

