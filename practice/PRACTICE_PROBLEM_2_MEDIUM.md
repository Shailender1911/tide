# Practice Problem 2: Payment Processing API (Medium)

## Context
A junior developer has written a payment processing endpoint for an e-commerce platform. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15-18

---

## Code to Review

```java
package com.ecommerce.payments;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Payment controller for processing orders. Added admin override for
 * refunds when the payment gateway is down!!!
 */
@RequestMapping("/api/v1/payments/")
@RestController
public class PaymentController {

    @Autowired
    public OrderService orderService;

    @Autowired
    public PaymentGateway paymentGateway;

    @Autowired
    public NotificationService notificationService;

    public static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @PutMapping("/process/{orderId}")
    public void processPayment(@PathVariable String orderId,
                               @RequestParam double amount,
                               @RequestParam String cardNumber,
                               @RequestParam String cvv,
                               @RequestParam String expiryDate) {

        Order order = orderService.getOrder(orderId);
        
        if (order.getAmount() == amount) {
            
            PaymentResult result = paymentGateway.charge(cardNumber, cvv, expiryDate, amount);
            
            if (result.isSuccess()) {
                order.setStatus("PAID");
                order.setPaymentId(new Random().nextInt(999999));
                orderService.save(order);
                notificationService.sendReceipt(order.getCustomerEmail(), amount);
            } else {
                throw new InternalServerError();
            }
        } else {
            throw new InternalServerError();
        }
    }

    @GetMapping("/refund")
    public void refundPayment(@RequestParam String orderId,
                              @RequestParam double refundAmount,
                              @RequestParam boolean isAdminOverride) {

        Order order = orderService.getOrder(orderId);

        if (!isAdminOverride) {
            if (order.getCustomerId() != AuthContext.getCurrentUserId()) {
                throw new InternalServerError();
            }
        }

        if (refundAmount < order.getAmount()) {
            order.setStatus("REFUNDED");
            order.setRefundAmount(refundAmount);
            orderService.save(order);

            paymentGateway.refund(order.getPaymentId(), refundAmount);
            notificationService.sendRefundConfirmation(order.getCustomerEmail());
        }
    }

    @PostMapping("/webhook/payment-status")
    public void handleWebhook(@RequestBody String payload) {
        // Parse webhook and update order
        JSONObject json = new JSONObject(payload);
        String orderId = json.getString("order_id");
        String status = json.getString("status");
        
        Order order = orderService.getOrder(orderId);
        order.setStatus(status);
        orderService.save(order);
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Categorize them by severity (CRITICAL, MAJOR, MINOR)
3. Suggest fixes for each issue
4. Focus on financial/payment-specific concerns

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- PCI DSS compliance (payment card security)
- Authorization bypass vulnerabilities
- Transaction atomicity
- Money precision (double vs BigDecimal)
- HTTP methods and idempotency
- Webhook security
- Error handling
- Response bodies
- Comparison operators

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Issues

1. **Card details in URL/params** (Lines 27-29)
   ```java
   @RequestParam String cardNumber,
   @RequestParam String cvv,
   @RequestParam String expiryDate
   // PCI DSS violation! Card data in URL gets logged everywhere
   // Fix: Use @RequestBody with HTTPS, tokenize card data
   ```

2. **Admin bypass from client** (Lines 51, 54)
   ```java
   @RequestParam boolean isAdminOverride
   if (!isAdminOverride) { ... }
   // Anyone can set isAdminOverride=true to bypass auth!
   // Fix: Check admin role server-side with @PreAuthorize
   ```

3. **No transaction management** (Lines 38-42)
   ```java
   order.setStatus("PAID");
   orderService.save(order);
   notificationService.sendReceipt(...);
   // If notification fails, payment is still recorded
   // If save fails after charge, money taken but order not updated
   // Fix: Add @Transactional, handle external calls properly
   ```

4. **double for money** (Lines 26, 50)
   ```java
   @RequestParam double amount
   @RequestParam double refundAmount
   // Precision errors in financial calculations!
   // Fix: Use BigDecimal
   ```

5. **Webhook has no authentication** (Lines 70-79)
   ```java
   @PostMapping("/webhook/payment-status")
   public void handleWebhook(@RequestBody String payload)
   // Anyone can call this and modify order status!
   // Fix: Verify webhook signature from payment provider
   ```

### 🔴 MAJOR Issues

6. **No response body** (Lines 25, 48)
   ```java
   public void processPayment(...)
   public void refundPayment(...)
   // Client gets no confirmation, payment ID, receipt number
   // Fix: Return ResponseEntity<PaymentResponse>
   ```

7. **Wrong HTTP methods** (Lines 24, 48)
   ```java
   @PutMapping("/process/{orderId}")  // Should be POST (not idempotent)
   @GetMapping("/refund")  // Should be POST (state-changing operation)
   ```

8. **Random payment ID** (Line 39)
   ```java
   order.setPaymentId(new Random().nextInt(999999));
   // Collision risk, should use UUID or payment gateway's ID
   ```

9. **Wrong comparison with ==** (Line 33, 56)
   ```java
   if (order.getAmount() == amount)  // For double, use comparison with tolerance
   if (order.getCustomerId() != AuthContext.getCurrentUserId())  // Use .equals()
   ```

10. **Refund logic inverted** (Line 60)
    ```java
    if (refundAmount < order.getAmount())
    // Should be <= (allow full refund)
    // Also: no check if already refunded, no check if payment exists
    ```

11. **No null checks** (Lines 32, 53, 75)
    ```java
    Order order = orderService.getOrder(orderId);
    order.getAmount()  // NPE if order not found
    ```

12. **Order status saved before refund** (Lines 61-65)
    ```java
    order.setStatus("REFUNDED");
    orderService.save(order);
    paymentGateway.refund(...);  // What if this fails?
    // Fix: Refund first, then update status, use @Transactional
    ```

### 🟡 MEDIUM Issues

13. **InternalServerError for all failures** (Lines 43, 46)
    ```java
    throw new InternalServerError();
    // Should be: 400 for amount mismatch, 402 for payment failure
    ```

14. **Field injection** (Lines 14-21)
    ```java
    @Autowired public OrderService
    // Should be constructor injection with private final
    ```

15. **Logger not used**
    - Payment operations should be logged for audit
    - No logging of payment attempts, successes, failures

16. **No input validation**
    - No validation on amount > 0
    - No validation on orderId format

17. **Webhook accepts any status** (Line 78)
    ```java
    order.setStatus(status);  // Should validate allowed statuses
    ```

### 🟢 MINOR Issues

18. **Trailing slash in path** (Line 10)
    ```java
    @RequestMapping("/api/v1/payments/")  // Inconsistent URL design
    ```

19. **No idempotency key for payments**
    - Payment could be charged twice on retry

20. **Silent failure in refund** (Lines 60-67)
    - If refundAmount > order.getAmount(), nothing happens
    - No error returned to client

</details>

