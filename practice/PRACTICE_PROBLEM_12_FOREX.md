# Practice Problem 12: Currency Exchange API (Hard)

## Context
A junior developer has written a currency exchange API for a banking application. This handles foreign exchange rate lookups, conversions, and transactions. Review this code and identify all issues.

**Time Limit**: 40 minutes  
**Difficulty**: Hard  
**Expected Issues**: 20+

---

## Code to Review

```java
package com.bank.forex;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Currency exchange controller.
 * Handles FX rates and currency conversion transactions.
 */
@RequestMapping("/forex")
@RestController
public class ForexController {

    @Autowired
    public AccountRepository accountRepository;

    @Autowired
    public ExchangeRateService rateService;

    @Autowired
    public TransactionRepository transactionRepository;

    public static final Logger logger = LoggerFactory.getLogger(ForexController.class);

    // Cache exchange rates for performance
    private static Map<String, Double> rateCache = new HashMap<>();
    private static long lastCacheUpdate = 0;

    @GetMapping("/rate")
    public double getExchangeRate(@RequestParam String fromCurrency,
                                  @RequestParam String toCurrency) {

        String key = fromCurrency + "-" + toCurrency;

        // Refresh cache every 5 minutes
        if (System.currentTimeMillis() - lastCacheUpdate > 300000) {
            rateCache.clear();
            lastCacheUpdate = System.currentTimeMillis();
        }

        if (!rateCache.containsKey(key)) {
            double rate = rateService.getRate(fromCurrency, toCurrency);
            rateCache.put(key, rate);
        }

        return rateCache.get(key);
    }

    @PostMapping("/convert")
    public void convertCurrency(@RequestParam String accountId,
                               @RequestParam String fromCurrency,
                               @RequestParam String toCurrency,
                               @RequestParam double amount,
                               @RequestParam double customRate) {

        Account account = accountRepository.findById(accountId).get();

        double rate;
        if (customRate > 0) {
            rate = customRate;  // Allow override for preferred customers
        } else {
            rate = getExchangeRate(fromCurrency, toCurrency);
        }

        double convertedAmount = amount * rate;
        double fee = amount * 0.02;  // 2% fee

        // Deduct from source currency balance
        Map<String, Double> balances = account.getCurrencyBalances();
        double currentBalance = balances.get(fromCurrency);
        
        if (currentBalance > amount + fee) {
            balances.put(fromCurrency, currentBalance - amount - fee);
            balances.put(toCurrency, balances.getOrDefault(toCurrency, 0.0) + convertedAmount);
            
            accountRepository.save(account);

            // Record transaction
            ForexTransaction tx = new ForexTransaction();
            tx.setId(System.currentTimeMillis() + "" + new Random().nextInt(100));
            tx.setAccountId(accountId);
            tx.setFromCurrency(fromCurrency);
            tx.setToCurrency(toCurrency);
            tx.setAmount(amount);
            tx.setRate(rate);
            tx.setFee(fee);
            tx.setConvertedAmount(convertedAmount);
            tx.setTimestamp(new Date());
            transactionRepository.save(tx);

            logger.info("FX transaction: " + amount + " " + fromCurrency + " to " + toCurrency);
        } else {
            throw new InternalServerError();
        }
    }

    @GetMapping("/history/{accountId}")
    public List<ForexTransaction> getHistory(@PathVariable String accountId,
                                             @RequestParam int limit) {
        return transactionRepository.findByAccountIdOrderByTimestampDesc(accountId)
            .stream().limit(limit).toList();
    }

    @PostMapping("/bulk-convert")
    public void bulkConvert(@RequestBody List<ConversionRequest> requests,
                           @RequestParam boolean skipValidation) {

        for (ConversionRequest req : requests) {
            if (!skipValidation) {
                validateRequest(req);
            }
            convertCurrency(req.getAccountId(), req.getFrom(), req.getTo(), 
                           req.getAmount(), req.getCustomRate());
        }
    }

    private void validateRequest(ConversionRequest req) {
        // Validation logic
    }

    @PostMapping("/set-preferred-rate")
    public void setPreferredRate(@RequestParam String customerId,
                                @RequestParam String currencyPair,
                                @RequestParam double rate) {

        // For VIP customers
        rateCache.put("VIP-" + customerId + "-" + currencyPair, rate);
    }

    @GetMapping("/calculate")
    public double calculateConversion(@RequestParam String from,
                                     @RequestParam String to,
                                     @RequestParam double amount) {
        double rate = getExchangeRate(from, to);
        return amount * rate;
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Financial precision, Rate manipulation, Transaction atomicity, Authorization
3. Consider forex trading regulations and best practices

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- customRate from client (anyone can set their own exchange rate!)
- skipValidation in bulk convert
- double for all money and rates (precision!)
- Static rate cache issues (thread safety, stale rates)
- No authorization - anyone can convert on any account
- setPreferredRate allows rate manipulation
- No @Transactional for conversion
- Balance check uses > instead of >=
- Fee calculation precision
- Rate could be negative or zero

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Financial/Security Issues

1. **customRate from client** (Lines 51, 54-56)
   ```java
   @RequestParam double customRate
   if (customRate > 0) {
       rate = customRate;
   }
   // CRITICAL: Anyone can set their own exchange rate!
   // Convert $100 at rate 1000 = get $100,000
   // Infinite money exploit
   // Fix: Remove this parameter entirely
   ```

2. **setPreferredRate endpoint** (Lines 110-116)
   ```java
   @PostMapping("/set-preferred-rate")
   public void setPreferredRate(@RequestParam String customerId,
                               @RequestParam String currencyPair,
                               @RequestParam double rate)
   // Anyone can set any exchange rate for any customer!
   // No authorization, no validation
   // Fix: Admin-only, audit logging, rate boundaries
   ```

3. **skipValidation from client** (Lines 97, 100-102)
   ```java
   @RequestParam boolean skipValidation
   if (!skipValidation) {
       validateRequest(req);
   }
   // Bypasses all validation checks
   ```

4. **No authorization anywhere**
   ```java
   @PostMapping("/convert")
   @GetMapping("/history/{accountId}")
   @PostMapping("/bulk-convert")
   // User A can convert User B's currency
   // User A can see User B's transaction history
   // Fix: Verify account ownership
   ```

5. **double for ALL financial calculations** (Throughout)
   ```java
   double amount, double rate, double convertedAmount, double fee
   double convertedAmount = amount * rate;
   double fee = amount * 0.02;
   // FX rates like 1.23456789 lose precision
   // $1000000 * 1.23456789 will have errors
   // Fix: BigDecimal with proper scale (8 decimals for FX)
   ```

6. **No @Transactional for conversion** (Lines 47-86)
   ```java
   balances.put(fromCurrency, ...);
   balances.put(toCurrency, ...);
   accountRepository.save(account);
   transactionRepository.save(tx);
   // If transaction save fails, balance already changed
   // Account in inconsistent state
   ```

### 🔴 MAJOR Issues

7. **Static cache not thread-safe** (Lines 25-26)
   ```java
   private static Map<String, Double> rateCache = new HashMap<>();
   private static long lastCacheUpdate = 0;
   // HashMap not thread-safe
   // lastCacheUpdate not volatile - visibility issues
   // Race condition in refresh logic
   ```

8. **Stale rate risk** (Lines 34-42)
   ```java
   if (System.currentTimeMillis() - lastCacheUpdate > 300000) {
       rateCache.clear();
   }
   // 5 minute cache for FX rates is DANGEROUS
   // Rates can move significantly in 5 minutes
   // Could convert at very wrong rate
   ```

9. **Balance check uses >** (Line 66)
   ```java
   if (currentBalance > amount + fee) {
   // Should be >= to allow using exact balance
   ```

10. **No validation on rate value** (Lines 54-56, 113)
    ```java
    if (customRate > 0) { rate = customRate; }
    // What if rate is 0.0001 or 1000000?
    // Should validate against market rate ± tolerance
    ```

11. **Transaction ID predictable** (Line 74)
    ```java
    tx.setId(System.currentTimeMillis() + "" + new Random().nextInt(100));
    // Collision risk, predictable
    // Fix: UUID
    ```

12. **No response body for convert** (Line 47)
    ```java
    public void convertCurrency(...)
    // Client gets no confirmation, transaction ID, rates used
    ```

13. **Optional.get() without check** (Line 53)
    ```java
    accountRepository.findById(accountId).get()
    ```

14. **NullPointerException on balance lookup** (Line 64)
    ```java
    double currentBalance = balances.get(fromCurrency);
    // If user doesn't have this currency, returns null
    // Unboxing null to double throws NPE
    // Fix: balances.getOrDefault(fromCurrency, 0.0)
    ```

### 🟡 MEDIUM Issues

15. **Field injection** (Lines 14-21)
    ```java
    @Autowired public AccountRepository
    ```

16. **InternalServerError for insufficient balance** (Line 85)
    ```java
    throw new InternalServerError();
    // Should be 400/422 with "Insufficient balance" message
    ```

17. **No input validation**
    - Currency codes not validated (should be ISO 4217)
    - Amount could be negative or zero
    - Limit in history could be negative

18. **Bulk convert not atomic** (Lines 96-105)
    ```java
    for (ConversionRequest req : requests) {
        convertCurrency(...);
    }
    // Partial success - some convert, some fail
    // No rollback
    ```

19. **java.util.Date** (Line 81)
    ```java
    tx.setTimestamp(new Date());
    ```

20. **Fee calculation in multiple places**
    ```java
    double fee = amount * 0.02;
    // Should be configurable constant
    // Different accounts might have different fee structures
    ```

### 🟢 MINOR Issues

21. **Logger info minimal** (Line 83)
    ```java
    logger.info("FX transaction: " + amount + " " + fromCurrency + " to " + toCurrency);
    // Missing: transaction ID, rate used, fee, account ID
    ```

22. **History limit not bounded** (Lines 89-93)
    ```java
    @RequestParam int limit
    .stream().limit(limit)
    // Limit could be Integer.MAX_VALUE
    // Should cap at reasonable maximum
    ```

23. **Calculate endpoint redundant** (Lines 118-123)
    - Same as mentally doing getRate * amount
    - But exposes rate lookup publicly

24. **Empty validation method** (Lines 107-109)
    ```java
    private void validateRequest(ConversionRequest req) {
        // Validation logic
    }
    // TODO left incomplete
    ```

### FX-Specific Issues

25. **No bid/ask spread**
    - Real FX has buy and sell rates
    - Single rate is unrealistic

26. **No rate timestamp**
    - Client doesn't know how old the rate is

27. **No maximum transaction limits**
    - Could convert millions without checks

</details>

