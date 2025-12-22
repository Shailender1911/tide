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

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Inventory Controller

```java
package com.warehouse.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Inventory controller for warehouse management.
 * Uses database for reservation tracking (not in-memory).
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private static final Duration RESERVATION_EXPIRY = Duration.ofMinutes(15);

    // FIX: Constructor injection
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public InventoryController(ProductRepository productRepository,
                               ReservationRepository reservationRepository,
                               ReservationService reservationService) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    /**
     * Reserve stock for an order.
     */
    @PostMapping("/reserve/{productId}")
    @Transactional(rollbackFor = Exception.class)  // FIX: Atomic reservation
    public ResponseEntity<ReservationResponse> reserveStock(
            @PathVariable String productId,
            @Valid @RequestBody ReservationRequest request) {

        logger.info("Reserve request - product: {}, quantity: {}, order: {}", 
                   productId, request.getQuantity(), request.getOrderId());

        // FIX: Proper null handling with custom exception
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // FIX: Calculate reserved from DATABASE (not in-memory)
        int totalReserved = reservationRepository.sumActiveReservationsByProductId(productId);
        int available = product.getStockQuantity() - totalReserved;

        // FIX: Use >= for comparison
        if (available < request.getQuantity()) {
            logger.warn("Insufficient stock - product: {}, available: {}, requested: {}", 
                       productId, available, request.getQuantity());
            throw new InsufficientStockException(
                String.format("Not enough stock. Available: %d, Requested: %d", 
                             available, request.getQuantity()));
        }

        // FIX: UUID for reservation ID
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setProductId(productId);
        reservation.setOrderId(request.getOrderId());
        reservation.setQuantity(request.getQuantity());
        reservation.setCreatedAt(Instant.now());
        reservation.setExpiresAt(Instant.now().plus(RESERVATION_EXPIRY));  // FIX: Use Duration
        reservation.setStatus("ACTIVE");

        reservationRepository.save(reservation);

        logger.info("Reservation created - id: {}, product: {}, quantity: {}", 
                   reservation.getId(), productId, request.getQuantity());

        // FIX: Return proper response
        ReservationResponse response = new ReservationResponse(
            reservation.getId(),
            productId,
            request.getQuantity(),
            reservation.getExpiresAt(),
            "RESERVED"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cancel a reservation.
     */
    @DeleteMapping("/reserve/{reservationId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> cancelReservation(@PathVariable String reservationId) {

        logger.info("Cancel reservation request - id: {}", reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // FIX: Just update status in database (no in-memory map to update)
        reservation.setStatus("CANCELLED");
        reservation.setCancelledAt(Instant.now());
        reservationRepository.save(reservation);

        logger.info("Reservation cancelled - id: {}, product: {}", 
                   reservationId, reservation.getProductId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Adjust stock (manager only).
     */
    @PutMapping("/adjust")
    @PreAuthorize("hasRole('MANAGER')")  // FIX: Server-side role check
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<StockAdjustmentResponse> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request) {

        String currentUser = SecurityContext.getCurrentUserId();
        
        logger.info("Stock adjustment - product: {}, adjustment: {}, user: {}, reason: {}", 
                   request.getProductId(), request.getAdjustment(), currentUser, request.getReason());

        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        int oldQuantity = product.getStockQuantity();
        int newQuantity = oldQuantity + request.getAdjustment();

        // FIX: Validate stock doesn't go negative
        if (newQuantity < 0) {
            throw new InvalidStockAdjustmentException(
                String.format("Adjustment would result in negative stock. Current: %d, Adjustment: %d", 
                             oldQuantity, request.getAdjustment()));
        }

        product.setStockQuantity(newQuantity);
        productRepository.save(product);

        // FIX: Detailed audit logging
        logger.info("Stock adjusted - product: {}, old: {}, new: {}, adjustedBy: {}, reason: {}", 
                   request.getProductId(), oldQuantity, newQuantity, currentUser, request.getReason());

        return ResponseEntity.ok(new StockAdjustmentResponse(
            request.getProductId(),
            oldQuantity,
            newQuantity,
            currentUser,
            Instant.now()
        ));
    }

    /**
     * Check product availability.
     */
    @GetMapping("/check/{productId}")
    public ResponseEntity<AvailabilityResponse> checkAvailability(@PathVariable String productId) {

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // FIX: Calculate from database
        int totalReserved = reservationRepository.sumActiveReservationsByProductId(productId);
        int available = product.getStockQuantity() - totalReserved;

        return ResponseEntity.ok(new AvailabilityResponse(
            productId,
            product.getStockQuantity(),
            totalReserved,
            available
        ));
    }

    /**
     * Bulk reserve with atomic transaction.
     */
    @PostMapping("/bulk-reserve")
    @Transactional(rollbackFor = Exception.class)  // FIX: All or nothing
    public ResponseEntity<BulkReservationResponse> bulkReserve(
            @Valid @RequestBody List<ReservationRequest> requests) {

        List<ReservationResponse> results = new ArrayList<>();

        // FIX: Validate ALL first
        for (ReservationRequest req : requests) {
            Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(req.getProductId()));
            
            int available = product.getStockQuantity() - 
                reservationRepository.sumActiveReservationsByProductId(req.getProductId());
            
            if (available < req.getQuantity()) {
                throw new InsufficientStockException(
                    "Not enough stock for product: " + req.getProductId());
            }
        }

        // Process all (will all succeed or all fail due to @Transactional)
        for (ReservationRequest req : requests) {
            Reservation reservation = createReservation(req);
            results.add(new ReservationResponse(
                reservation.getId(),
                req.getProductId(),
                req.getQuantity(),
                reservation.getExpiresAt(),
                "RESERVED"
            ));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new BulkReservationResponse(results, "ALL_RESERVED"));
    }

    private Reservation createReservation(ReservationRequest request) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setProductId(request.getProductId());
        reservation.setOrderId(request.getOrderId());
        reservation.setQuantity(request.getQuantity());
        reservation.setCreatedAt(Instant.now());
        reservation.setExpiresAt(Instant.now().plus(RESERVATION_EXPIRY));
        reservation.setStatus("ACTIVE");
        return reservationRepository.save(reservation);
    }
}
```

### Repository with Reservation Sum Query

```java
public interface ReservationRepository extends JpaRepository<Reservation, String> {
    
    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM Reservation r " +
           "WHERE r.productId = :productId " +
           "AND r.status = 'ACTIVE' " +
           "AND r.expiresAt > CURRENT_TIMESTAMP")
    int sumActiveReservationsByProductId(@Param("productId") String productId);
}
```

### DTOs

```java
public class ReservationRequest {
    @NotBlank
    private String orderId;

    @NotBlank
    private String productId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}

public record ReservationResponse(
    String reservationId,
    String productId,
    int quantity,
    Instant expiresAt,
    String status
) {}

public class StockAdjustmentRequest {
    @NotBlank
    private String productId;

    private int adjustment;  // Can be positive or negative

    @NotBlank
    private String reason;
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| Reservation tracking | Static HashMap | Database query |
| Thread safety | HashMap | Database + @Transactional |
| Manager check | `isManager` from client | `@PreAuthorize("hasRole('MANAGER')")` |
| Quantity comparison | `>` | `<` (checking if not enough) |
| Stock negative | No check | Validate newQuantity >= 0 |
| Bulk atomicity | For loop, partial success | @Transactional, all or nothing |
| Reservation ID | orderId + productId | UUID |
| Response | void | ResponseEntity with details |
| Expiry | Magic number | Duration.ofMinutes(15) |

</details>

