# Practice Problem 4: Inventory Management API (Medium)

## Context
A junior developer has written an inventory management system for a warehouse. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.warehouse.inventory;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Inventory controller for warehouse management.
 * Supports reservations for orders and manual stock adjustments.
 */
@RequestMapping("/inventory")
@RestController
public class InventoryController {

    @Autowired
    public ProductRepository productRepository;

    @Autowired
    public ReservationRepository reservationRepository;

    public static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    // In-memory reservation tracking for speed
    private static Map<String, Integer> reservedQuantities = new HashMap<>();

    @PostMapping("/reserve/{productId}")
    public void reserveStock(@PathVariable String productId,
                             @RequestParam int quantity,
                             @RequestParam String orderId) {

        Product product = productRepository.findById(productId).get();
        int available = product.getStockQuantity() - reservedQuantities.getOrDefault(productId, 0);

        if (available > quantity) {
            reservedQuantities.put(productId, 
                reservedQuantities.getOrDefault(productId, 0) + quantity);

            Reservation reservation = new Reservation();
            reservation.setId(orderId + "-" + productId);
            reservation.setProductId(productId);
            reservation.setQuantity(quantity);
            reservation.setCreatedAt(new Date());
            reservation.setExpiresAt(new Date(System.currentTimeMillis() + 900000)); // 15 mins
            reservationRepository.save(reservation);
        } else {
            throw new RuntimeException("Not enough stock");
        }
    }

    @DeleteMapping("/reserve/{reservationId}")
    public void cancelReservation(@PathVariable String reservationId) {
        
        Reservation reservation = reservationRepository.findById(reservationId).get();
        
        reservedQuantities.put(reservation.getProductId(),
            reservedQuantities.get(reservation.getProductId()) - reservation.getQuantity());
        
        reservationRepository.delete(reservation);
    }

    @PutMapping("/adjust")
    public void adjustStock(@RequestParam String productId,
                           @RequestParam int adjustment,
                           @RequestParam String reason,
                           @RequestParam boolean isManager) {

        if (!isManager) {
            throw new InternalServerError();
        }

        Product product = productRepository.findById(productId).get();
        product.setStockQuantity(product.getStockQuantity() + adjustment);
        productRepository.save(product);
        
        logger.info("Stock adjusted by " + adjustment);
    }

    @GetMapping("/check/{productId}")
    public int checkAvailability(@PathVariable String productId) {
        Product product = productRepository.findById(productId).get();
        return product.getStockQuantity() - reservedQuantities.getOrDefault(productId, 0);
    }

    @PostMapping("/bulk-reserve")
    public void bulkReserve(@RequestBody List<ReservationRequest> requests) {
        for (ReservationRequest req : requests) {
            reserveStock(req.getProductId(), req.getQuantity(), req.getOrderId());
        }
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Concurrency, Data consistency, Authorization, API design
3. Suggest fixes for each issue

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Issues

1. **isManager from client input** (Lines 61, 63)
   ```java
   @RequestParam boolean isManager
   if (!isManager) { throw... }
   // Anyone can adjust stock by setting isManager=true
   // Fix: @PreAuthorize("hasRole('MANAGER')")
   ```

2. **Non-thread-safe HashMap** (Line 23)
   ```java
   private static Map<String, Integer> reservedQuantities = new HashMap<>();
   // HashMap is not thread-safe! Race conditions will corrupt data
   // Multiple concurrent reservations = incorrect quantities
   // Fix: Use ConcurrentHashMap or proper database locking
   ```

3. **Race condition in reservation** (Lines 31-36)
   ```java
   int available = product.getStockQuantity() - reservedQuantities.getOrDefault(...);
   if (available > quantity) {
       reservedQuantities.put(...);
   }
   // Another thread could reserve between check and put
   // Fix: Use atomic operations or database-level locking
   ```

4. **In-memory state will be lost** (Line 23)
   ```java
   private static Map<String, Integer> reservedQuantities
   // Server restart = all reservation tracking lost!
   // But database still has reservations
   // Fix: Calculate reserved from database, or use distributed cache
   ```

5. **No @Transactional** (Lines 34-43)
   ```java
   reservedQuantities.put(...);
   reservationRepository.save(reservation);
   // If DB save fails, in-memory map is already updated = inconsistent!
   ```

### 🔴 MAJOR Issues

6. **NPE in cancelReservation** (Line 54)
   ```java
   reservedQuantities.get(reservation.getProductId()) - reservation.getQuantity()
   // .get() returns null if key doesn't exist (server restarted)
   // This will throw NullPointerException
   ```

7. **No response bodies** (All endpoints return void)
   ```java
   public void reserveStock(...)
   // Client doesn't get reservation ID, confirmation, expiry time
   ```

8. **Optional.get() without check** (Lines 30, 51, 68, 76)
   ```java
   productRepository.findById(productId).get()
   // Throws NoSuchElementException if not found
   // Fix: orElseThrow(() -> new ProductNotFoundException(...))
   ```

9. **Wrong comparison operator** (Line 33)
   ```java
   if (available > quantity)
   // Should be >= to allow reserving exact available quantity
   ```

10. **Stock can go negative** (Line 69)
    ```java
    product.setStockQuantity(product.getStockQuantity() + adjustment);
    // adjustment could be negative and make stock negative
    // No validation that resulting stock >= 0
    ```

11. **Bulk reserve not atomic** (Lines 82-85)
    ```java
    for (ReservationRequest req : requests) {
        reserveStock(...);
    }
    // Partial success possible - some reserved, some not
    // No rollback if one fails
    ```

### 🟡 MEDIUM Issues

12. **InternalServerError for authorization** (Line 64)
    ```java
    throw new InternalServerError();
    // Should be 403 Forbidden
    ```

13. **RuntimeException for business error** (Line 46)
    ```java
    throw new RuntimeException("Not enough stock");
    // Should be specific exception with 400/409 status
    ```

14. **Field injection** (Lines 14-18)
    ```java
    @Autowired public ProductRepository
    // Should be constructor injection
    ```

15. **Poor audit logging** (Line 72)
    ```java
    logger.info("Stock adjusted by " + adjustment);
    // Missing: productId, who adjusted, reason, old/new values
    ```

16. **java.util.Date usage** (Lines 41-42)
    ```java
    new Date()
    new Date(System.currentTimeMillis() + 900000)
    // Use Instant or LocalDateTime
    ```

17. **No input validation**
    - quantity could be <= 0
    - adjustment could make stock negative
    - reason could be empty

### 🟢 MINOR Issues

18. **Magic number for expiry** (Line 42)
    ```java
    900000  // 15 mins in milliseconds
    // Use Duration.ofMinutes(15).toMillis() or config property
    ```

19. **Reservation ID format** (Line 38)
    ```java
    reservation.setId(orderId + "-" + productId);
    // Could collide if same order reserves same product twice
    ```

20. **No endpoint for expired reservation cleanup**
    - Reservations expire but reserved quantities stay in map

21. **Static state in controller** (Line 23)
    - Should be in a service layer
    - Problematic in distributed/clustered environments

</details>

