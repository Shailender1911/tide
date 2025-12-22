# Practice Problem 8: Subscription Billing API (Hard)

## Context
A junior developer has written a subscription management API for a SaaS platform. This handles plan management, billing, and cancellations. Review this code and identify all issues.

**Time Limit**: 40 minutes  
**Difficulty**: Hard  
**Expected Issues**: 20+

---

## Code to Review

```java
package com.saas.billing;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Subscription controller for managing customer subscriptions.
 * Added override for sales team to give discounts.
 */
@RequestMapping("/subscriptions")
@RestController
public class SubscriptionController {

    @Autowired
    public SubscriptionRepository subscriptionRepository;

    @Autowired
    public CustomerRepository customerRepository;

    @Autowired
    public PaymentService paymentService;

    @Autowired
    public InvoiceService invoiceService;

    public static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    // Cache subscription prices
    private static HashMap<String, Double> planPrices = new HashMap<>() {{
        put("BASIC", 9.99);
        put("PRO", 29.99);
        put("ENTERPRISE", 99.99);
    }};

    @PostMapping("/create")
    public void createSubscription(@RequestParam String customerId,
                                   @RequestParam String planId,
                                   @RequestParam String cardToken,
                                   @RequestParam double discountPercent,
                                   @RequestParam boolean trialEnabled) {

        Customer customer = customerRepository.findById(customerId).get();

        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID().toString());
        sub.setCustomerId(customerId);
        sub.setPlanId(planId);
        sub.setStatus("ACTIVE");

        double price = planPrices.get(planId);
        double finalPrice = price - (price * discountPercent / 100);
        sub.setMonthlyPrice(finalPrice);

        if (trialEnabled) {
            sub.setTrialEndDate(new Date(System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000));
            sub.setStatus("TRIAL");
        } else {
            // Charge immediately
            paymentService.charge(cardToken, finalPrice);
        }

        sub.setCreatedAt(new Date());
        sub.setNextBillingDate(new Date(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000));
        subscriptionRepository.save(sub);
    }

    @PostMapping("/cancel/{subscriptionId}")
    public void cancelSubscription(@PathVariable String subscriptionId,
                                   @RequestParam boolean immediateCancel,
                                   @RequestParam boolean refundEnabled) {

        Subscription sub = subscriptionRepository.findById(subscriptionId).get();

        if (immediateCancel) {
            sub.setStatus("CANCELLED");
            sub.setCancelledAt(new Date());

            if (refundEnabled) {
                double daysRemaining = calculateDaysRemaining(sub);
                double refundAmount = (sub.getMonthlyPrice() / 30) * daysRemaining;
                paymentService.refund(sub.getLastPaymentId(), refundAmount);
            }
        } else {
            sub.setStatus("PENDING_CANCEL");
            sub.setCancelAtPeriodEnd(true);
        }

        subscriptionRepository.save(sub);
    }

    @PutMapping("/upgrade/{subscriptionId}")
    public void upgradePlan(@PathVariable String subscriptionId,
                           @RequestParam String newPlanId,
                           @RequestParam boolean chargeImmediately) {

        Subscription sub = subscriptionRepository.findById(subscriptionId).get();

        double oldPrice = sub.getMonthlyPrice();
        double newPrice = planPrices.get(newPlanId);

        if (newPrice > oldPrice) {
            sub.setPlanId(newPlanId);
            sub.setMonthlyPrice(newPrice);

            if (chargeImmediately) {
                double proratedAmount = (newPrice - oldPrice) / 30 * calculateDaysRemaining(sub);
                paymentService.charge(sub.getCardToken(), proratedAmount);
            }
        }

        subscriptionRepository.save(sub);
    }

    @GetMapping("/billing-history/{customerId}")
    public List<Invoice> getBillingHistory(@PathVariable String customerId) {
        return invoiceService.getInvoicesForCustomer(customerId);
    }

    @PostMapping("/apply-coupon")
    public void applyCoupon(@RequestParam String subscriptionId,
                           @RequestParam String couponCode,
                           @RequestParam int discountMonths) {

        Subscription sub = subscriptionRepository.findById(subscriptionId).get();
        
        // Apply discount
        sub.setDiscountEndDate(new Date(System.currentTimeMillis() + 
            discountMonths * 30 * 24 * 60 * 60 * 1000));
        sub.setCouponCode(couponCode);
        subscriptionRepository.save(sub);
    }

    private double calculateDaysRemaining(Subscription sub) {
        return (sub.getNextBillingDate().getTime() - System.currentTimeMillis()) 
            / (24 * 60 * 60 * 1000);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void processRecurringBilling() {
        List<Subscription> subs = subscriptionRepository.findByNextBillingDateBefore(new Date());
        
        for (Subscription sub : subs) {
            if (sub.getStatus() == "ACTIVE") {
                paymentService.charge(sub.getCardToken(), sub.getMonthlyPrice());
                sub.setNextBillingDate(new Date(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000));
                subscriptionRepository.save(sub);
            }
        }
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Billing integrity, Authorization, Money handling, Recurring payment logic
3. Consider subscription business logic edge cases

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Client-controlled parameters (discountPercent, trialEnabled, refundEnabled, chargeImmediately, discountMonths)
- double for money and proration calculations
- No authorization - anyone can cancel/upgrade anyone's subscription
- Static price cache issues
- Transaction management for charges
- String comparison with == in scheduled job
- Integer overflow in date calculations
- No coupon validation
- No idempotency for billing
- Billing history authorization

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Security/Business Issues

1. **discountPercent from client** (Line 38, 48-49)
   ```java
   @RequestParam double discountPercent
   double finalPrice = price - (price * discountPercent / 100);
   // Anyone can give themselves 100% discount!
   // Fix: Validate coupon codes server-side, limit discount percentages
   ```

2. **trialEnabled from client** (Lines 39, 51)
   ```java
   @RequestParam boolean trialEnabled
   if (trialEnabled) { sub.setTrialEndDate(...); }
   // Anyone can get unlimited free trials
   // Fix: Check if customer already had trial, server-side eligibility
   ```

3. **refundEnabled from client** (Lines 65, 72)
   ```java
   @RequestParam boolean refundEnabled
   if (refundEnabled) { paymentService.refund(...); }
   // Anyone can trigger refunds on any subscription!
   // Fix: Server-side refund policy, role-based authorization
   ```

4. **No authorization on any endpoint**
   ```java
   @PostMapping("/cancel/{subscriptionId}")
   @PutMapping("/upgrade/{subscriptionId}")
   @GetMapping("/billing-history/{customerId}")
   // User A can cancel/modify User B's subscription
   // User A can see User B's billing history
   // Fix: Verify ownership in every endpoint
   ```

5. **discountMonths from client** (Lines 112, 117)
   ```java
   @RequestParam int discountMonths
   // Anyone can apply unlimited months of discount
   // No validation of coupon code
   ```

### 🔴 CRITICAL Financial Issues

6. **double for all money** (Throughout)
   ```java
   double price, finalPrice, refundAmount, proratedAmount
   // Precision errors in billing calculations
   // $9.99 * 12 might not equal $119.88 exactly
   // Fix: Use BigDecimal with HALF_UP rounding
   ```

7. **No transaction for charge + save** (Lines 54-60)
   ```java
   paymentService.charge(cardToken, finalPrice);
   // ... more code ...
   subscriptionRepository.save(sub);
   // If save fails, customer charged but no subscription record
   // Fix: @Transactional with proper error handling
   ```

8. **Billing without idempotency** (Lines 130-139)
   ```java
   @Scheduled(cron = "0 0 0 * * *")
   public void processRecurringBilling() {
       for (Subscription sub : subs) {
           paymentService.charge(...);
       }
   }
   // If job fails mid-way and restarts, some customers charged twice
   // Fix: Track billing attempts, use idempotency keys
   ```

9. **Integer overflow in date calculations** (Lines 52, 59, 117, 136)
   ```java
   new Date(System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000)
   // 14 * 24 * 60 * 60 * 1000 = 1,209,600,000 (int)
   // 30 * 24 * 60 * 60 * 1000 = 2,592,000,000 (OVERFLOW! > Integer.MAX_VALUE)
   // Fix: Use 30L * 24 * 60 * 60 * 1000 (long literal)
   ```

### 🔴 MAJOR Issues

10. **String comparison with ==** (Line 133)
    ```java
    if (sub.getStatus() == "ACTIVE")
    // Compares references, not values
    // Fix: "ACTIVE".equals(sub.getStatus())
    ```

11. **No response bodies** (All endpoints return void)
    ```java
    public void createSubscription(...)
    // No subscription ID, trial dates, or confirmation returned
    ```

12. **Static mutable cache** (Lines 27-31)
    ```java
    private static HashMap<String, Double> planPrices
    // Not thread-safe (HashMap)
    // Prices hardcoded - can't update without redeploy
    // Fix: Use database or config, ConcurrentHashMap if needed
    ```

13. **NPE on invalid plan** (Line 47, 92)
    ```java
    double price = planPrices.get(planId);
    // Returns null if plan doesn't exist - NPE on unboxing
    ```

14. **Optional.get() without check** (Lines 41, 67, 88, 114)
    ```java
    subscriptionRepository.findById(subscriptionId).get()
    ```

15. **Silent failure on downgrade** (Lines 86-101)
    ```java
    if (newPrice > oldPrice) {
        // upgrade logic
    }
    // No else - downgrade silently ignored, no error
    ```

16. **Card token stored in subscription** (Line 98)
    ```java
    paymentService.charge(sub.getCardToken(), proratedAmount);
    // Implies card token stored in DB - security concern
    // Should use payment method IDs or vault tokens
    ```

### 🟡 MEDIUM Issues

17. **Field injection** (Lines 14-24)

18. **Logger not used**
    - No audit of subscription changes
    - No logging of billing operations

19. **Refund calculation** (Lines 73-75)
    ```java
    double daysRemaining = calculateDaysRemaining(sub);
    double refundAmount = (sub.getMonthlyPrice() / 30) * daysRemaining;
    // Assumes 30 days in month - inaccurate
    // Should prorate based on actual billing period
    ```

20. **java.util.Date** (Throughout)
    - Use Instant, LocalDate, or LocalDateTime

21. **No validation on coupon code** (Lines 111-120)
    ```java
    sub.setCouponCode(couponCode);
    // Coupon not validated - could be expired, invalid, already used
    ```

### 🟢 MINOR Issues

22. **Magic numbers** (Lines 52, 59, 117, 136)
    ```java
    14, 30, 24, 60, 60, 1000
    // Should be named constants: TRIAL_DAYS, BILLING_CYCLE_DAYS
    ```

23. **No pagination on billing history** (Lines 105-107)

24. **Scheduled job in controller**
    - Should be in a separate service class

25. **No error handling in scheduled job** (Lines 128-139)
    - One failure stops all billing
    - Should continue and collect failures

</details>

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Subscription Controller

```java
package com.saas.billing;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);
    private static final Duration TRIAL_PERIOD = Duration.ofDays(14);
    private static final Duration BILLING_CYCLE = Duration.ofDays(30);

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final PlanService planService;  // FIX: Get prices from service/DB
    private final CouponService couponService;

    // Constructor injection...

    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();
        
        Customer customer = customerRepository.findById(request.getCustomerId())
            .orElseThrow(() -> new CustomerNotFoundException(request.getCustomerId()));

        // FIX: Authorization
        if (!Objects.equals(customer.getUserId(), currentUserId)) {
            throw new UnauthorizedException("Cannot create subscription for another customer");
        }

        // FIX: Get plan price from database (not client)
        Plan plan = planService.getPlan(request.getPlanId())
            .orElseThrow(() -> new PlanNotFoundException(request.getPlanId()));

        BigDecimal finalPrice = plan.getPrice();

        // FIX: Validate coupon server-side (not discount percentage from client)
        if (request.getCouponCode() != null) {
            Coupon coupon = couponService.validateAndApply(request.getCouponCode(), plan.getId());
            if (coupon != null) {
                BigDecimal discount = finalPrice.multiply(coupon.getDiscountRate());
                finalPrice = finalPrice.subtract(discount).setScale(2, RoundingMode.HALF_UP);
            }
        }

        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID().toString());
        sub.setCustomerId(request.getCustomerId());
        sub.setPlanId(request.getPlanId());
        sub.setMonthlyPrice(finalPrice);
        sub.setCreatedAt(Instant.now());

        // FIX: Trial eligibility is server-side decision
        if (customer.isEligibleForTrial()) {
            sub.setTrialEndDate(Instant.now().plus(TRIAL_PERIOD));
            sub.setStatus("TRIAL");
            customer.setTrialUsed(true);
            customerRepository.save(customer);
        } else {
            sub.setStatus("ACTIVE");
            sub.setNextBillingDate(Instant.now().plus(BILLING_CYCLE));
            
            // Charge immediately
            PaymentResult result = paymentService.charge(
                request.getPaymentMethodId(),  // FIX: Payment method, not card token
                finalPrice
            );
            
            if (!result.isSuccess()) {
                throw new PaymentFailedException(result.getErrorMessage());
            }
            
            sub.setLastPaymentId(result.getPaymentId());
        }

        subscriptionRepository.save(sub);

        logger.info("Subscription created - id: {}, plan: {}, price: {}", 
                   sub.getId(), request.getPlanId(), finalPrice);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(sub));
    }

    @PostMapping("/cancel/{subscriptionId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable String subscriptionId,
            @RequestBody CancelRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        // FIX: Authorization
        Customer customer = customerRepository.findById(sub.getCustomerId())
            .orElseThrow(() -> new CustomerNotFoundException(sub.getCustomerId()));

        if (!Objects.equals(customer.getUserId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized");
        }

        if (request.isImmediate()) {
            sub.setStatus("CANCELLED");
            sub.setCancelledAt(Instant.now());

            // FIX: Refund is server-side policy decision
            if (isEligibleForRefund(sub)) {
                BigDecimal refundAmount = calculateProRatedRefund(sub);
                if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    paymentService.refund(sub.getLastPaymentId(), refundAmount);
                    logger.info("Refund issued - subscription: {}, amount: {}", 
                               subscriptionId, refundAmount);
                }
            }
        } else {
            sub.setStatus("PENDING_CANCEL");
            sub.setCancelAtPeriodEnd(true);
        }

        subscriptionRepository.save(sub);

        logger.info("Subscription cancelled - id: {}, immediate: {}", 
                   subscriptionId, request.isImmediate());

        return ResponseEntity.ok(toResponse(sub));
    }

    @PutMapping("/upgrade/{subscriptionId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<SubscriptionResponse> upgradePlan(
            @PathVariable String subscriptionId,
            @RequestBody UpgradeRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        // Authorization...

        Plan newPlan = planService.getPlan(request.getNewPlanId())
            .orElseThrow(() -> new PlanNotFoundException(request.getNewPlanId()));

        BigDecimal oldPrice = sub.getMonthlyPrice();
        BigDecimal newPrice = newPlan.getPrice();

        // FIX: Handle both upgrade and downgrade
        if (newPrice.compareTo(oldPrice) > 0) {
            // Upgrade - charge prorated difference
            BigDecimal proratedAmount = calculateProRatedCharge(sub, newPrice.subtract(oldPrice));
            paymentService.charge(sub.getPaymentMethodId(), proratedAmount);
        }
        // Downgrade takes effect next billing cycle (no immediate refund)

        sub.setPlanId(request.getNewPlanId());
        sub.setMonthlyPrice(newPrice);
        subscriptionRepository.save(sub);

        logger.info("Subscription upgraded - id: {}, from: {} to: {}", 
                   subscriptionId, oldPrice, newPrice);

        return ResponseEntity.ok(toResponse(sub));
    }

    // FIX: Scheduled job should be in separate service, not controller
    // See: SubscriptionBillingService.java

    private BigDecimal calculateProRatedRefund(Subscription sub) {
        if (sub.getNextBillingDate() == null) return BigDecimal.ZERO;
        
        long daysRemaining = ChronoUnit.DAYS.between(Instant.now(), sub.getNextBillingDate());
        if (daysRemaining <= 0) return BigDecimal.ZERO;

        return sub.getMonthlyPrice()
            .multiply(BigDecimal.valueOf(daysRemaining))
            .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }

    private boolean isEligibleForRefund(Subscription sub) {
        // Server-side refund policy
        long daysSincePayment = ChronoUnit.DAYS.between(sub.getLastPaymentDate(), Instant.now());
        return daysSincePayment <= 7;  // 7-day refund window
    }
}
```

### Billing Service (Scheduled Job)

```java
@Service
public class SubscriptionBillingService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionBillingService.class);

    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    @Transactional
    public void processRecurringBilling() {
        List<Subscription> dueSubs = subscriptionRepository
            .findByNextBillingDateBeforeAndStatus(Instant.now(), "ACTIVE");

        List<BillingResult> results = new ArrayList<>();

        for (Subscription sub : dueSubs) {
            try {
                processSingleBilling(sub);
                results.add(new BillingResult(sub.getId(), true, null));
            } catch (Exception e) {
                // FIX: Continue with next, don't stop all billing
                logger.error("Billing failed for subscription: {}", sub.getId(), e);
                results.add(new BillingResult(sub.getId(), false, e.getMessage()));
                handleFailedBilling(sub);
            }
        }

        // Report results
        long successful = results.stream().filter(BillingResult::isSuccess).count();
        logger.info("Billing completed - total: {}, success: {}, failed: {}", 
                   results.size(), successful, results.size() - successful);
    }

    private void processSingleBilling(Subscription sub) {
        // FIX: Use .equals() for string comparison
        if (!"ACTIVE".equals(sub.getStatus())) return;

        PaymentResult result = paymentService.charge(
            sub.getPaymentMethodId(), 
            sub.getMonthlyPrice()
        );

        if (result.isSuccess()) {
            sub.setNextBillingDate(Instant.now().plus(Duration.ofDays(30)));
            sub.setLastPaymentId(result.getPaymentId());
            sub.setLastPaymentDate(Instant.now());
            subscriptionRepository.save(sub);
        } else {
            throw new PaymentFailedException(result.getErrorMessage());
        }
    }

    private void handleFailedBilling(Subscription sub) {
        sub.setFailedPaymentCount(sub.getFailedPaymentCount() + 1);
        if (sub.getFailedPaymentCount() >= 3) {
            sub.setStatus("SUSPENDED");
        }
        subscriptionRepository.save(sub);
    }
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| `discountPercent` from client | Any % allowed | Validate coupon server-side |
| `trialEnabled` from client | Free trials for all | Check customer.isEligibleForTrial() |
| `refundEnabled` from client | Anyone triggers refund | Server-side refund policy |
| Money | `double` | `BigDecimal` |
| Integer overflow | `30 * 24 * 60 * 60 * 1000` | `Duration.ofDays(30)` |
| String comparison | `==` | `.equals()` |
| Scheduled job errors | Stops all billing | Continue, collect failures |
| Card storage | Card token in DB | Payment method ID |

</details>

