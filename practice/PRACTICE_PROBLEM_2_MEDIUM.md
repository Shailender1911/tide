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

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Payment Controller

```java
package com.ecommerce.payments;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment controller for processing orders.
 * Handles payment processing, refunds, and webhook callbacks.
 */
@RestController
@RequestMapping("/api/v1/payments")  // FIX: No trailing slash
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    // FIX: Constructor injection with private final
    private final OrderService orderService;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;

    public PaymentController(OrderService orderService,
                            PaymentGateway paymentGateway,
                            NotificationService notificationService) {
        this.orderService = orderService;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
    }

    /**
     * Process payment for an order.
     */
    @PostMapping("/process/{orderId}")  // FIX: POST for processing payment
    @Transactional(rollbackFor = Exception.class)  // FIX: Atomic operation
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable String orderId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {  // FIX: Card details in body, not URL

        logger.info("Processing payment for orderId: {}, idempotencyKey: {}", orderId, idempotencyKey);

        // FIX: Null check with proper exception
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        // FIX: Use BigDecimal.compareTo() for money comparison
        if (request.getAmount().compareTo(order.getAmount()) != 0) {
            logger.warn("Amount mismatch for order: {}", orderId);
            throw new PaymentAmountMismatchException(
                "Expected: " + order.getAmount() + ", Received: " + request.getAmount());
        }

        // FIX: Use tokenized payment (card details never reach your server)
        PaymentResult result = paymentGateway.charge(
            request.getPaymentToken(),  // Token from payment provider
            request.getAmount()
        );

        if (!result.isSuccess()) {
            logger.warn("Payment failed for order: {}, reason: {}", orderId, result.getErrorMessage());
            throw new PaymentFailedException(result.getErrorMessage());
        }

        // FIX: UUID for payment ID
        String paymentId = UUID.randomUUID().toString();

        order.setStatus("PAID");
        order.setPaymentId(paymentId);
        order.setPaidAt(Instant.now());
        orderService.save(order);

        notificationService.sendReceipt(order.getCustomerEmail(), request.getAmount());

        logger.info("Payment successful - orderId: {}, paymentId: {}", orderId, paymentId);

        // FIX: Return proper response
        PaymentResponse response = PaymentResponse.builder()
            .paymentId(paymentId)
            .orderId(orderId)
            .amount(request.getAmount())
            .status("SUCCESS")
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Refund a payment.
     */
    @PostMapping("/refund/{orderId}")  // FIX: POST for state-changing operation
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<RefundResponse> refundPayment(
            @PathVariable String orderId,
            @Valid @RequestBody RefundRequest request) {

        String currentUserId = AuthContext.getCurrentUserId();
        logger.info("Refund request for order: {} by user: {}", orderId, currentUserId);

        Order order = orderService.getOrder(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        // FIX: Server-side authorization check
        if (!Objects.equals(order.getCustomerId(), currentUserId)) {
            logger.warn("Unauthorized refund attempt by: {} for order: {}", currentUserId, orderId);
            throw new UnauthorizedException("Not authorized to refund this order");
        }

        // FIX: Use BigDecimal.compareTo() with <= check
        if (request.getRefundAmount().compareTo(order.getAmount()) > 0) {
            throw new InvalidRefundException("Refund amount exceeds order amount");
        }

        // Check if already refunded
        if ("REFUNDED".equals(order.getStatus())) {
            throw new InvalidRefundException("Order already refunded");
        }

        // FIX: Refund FIRST, then update status
        paymentGateway.refund(order.getPaymentId(), request.getRefundAmount());

        order.setStatus("REFUNDED");
        order.setRefundAmount(request.getRefundAmount());
        order.setRefundedAt(Instant.now());
        orderService.save(order);

        notificationService.sendRefundConfirmation(order.getCustomerEmail());

        logger.info("Refund successful - orderId: {}, amount: {}", orderId, request.getRefundAmount());

        RefundResponse response = new RefundResponse(
            orderId,
            request.getRefundAmount(),
            "REFUNDED",
            Instant.now()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Handle payment webhook from payment provider.
     */
    @PostMapping("/webhook/payment-status")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader("X-Webhook-Signature") String signature,  // FIX: Verify signature
            @RequestBody String payload) {

        // FIX: Verify webhook signature from payment provider
        if (!paymentGateway.verifyWebhookSignature(payload, signature)) {
            logger.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WebhookPayload webhook = parseWebhook(payload);
        
        // FIX: Validate status against allowed values
        if (!isValidStatus(webhook.getStatus())) {
            logger.warn("Invalid status in webhook: {}", webhook.getStatus());
            return ResponseEntity.badRequest().build();
        }

        Order order = orderService.getOrder(webhook.getOrderId());
        if (order != null) {
            order.setStatus(webhook.getStatus());
            orderService.save(order);
            logger.info("Order status updated via webhook: {}", webhook.getOrderId());
        }

        return ResponseEntity.ok().build();
    }

    private boolean isValidStatus(String status) {
        return Set.of("PENDING", "PAID", "FAILED", "REFUNDED").contains(status);
    }
}
```

### DTOs

```java
// Payment Request - NEVER includes raw card details
public class PaymentRequest {
    @NotBlank
    private String paymentToken;  // Token from Stripe/PayPal frontend

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;  // FIX: BigDecimal for money
}

// Refund Request
public class RefundRequest {
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal refundAmount;

    private String reason;
}

// Payment Response
@Builder
public class PaymentResponse {
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String status;
    private Instant timestamp;
}

// Refund Response
public record RefundResponse(
    String orderId,
    BigDecimal refundAmount,
    String status,
    Instant refundedAt
) {}
```

### Custom Exceptions

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException { }

@ResponseStatus(HttpStatus.BAD_REQUEST)  // 400
public class PaymentAmountMismatchException extends RuntimeException { }

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)  // 402
public class PaymentFailedException extends RuntimeException { }

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRefundException extends RuntimeException { }

@ResponseStatus(HttpStatus.FORBIDDEN)  // 403
public class UnauthorizedException extends RuntimeException { }
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| Card details | In URL params | Tokenized payment, body only |
| Admin override | `isAdminOverride` from client | Server-side role check |
| Money type | `double` | `BigDecimal` |
| Transaction | Missing | `@Transactional` |
| Refund order | Status first, then refund | Refund first, then status |
| Webhook security | No verification | Signature verification |
| Response | `void` | `ResponseEntity<T>` |
| HTTP methods | PUT/GET | POST for both |
| Authorization | Client-controlled | Server-side check |

</details>

