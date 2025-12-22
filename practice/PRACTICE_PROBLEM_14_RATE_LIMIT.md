# Practice Problem 14: API Rate Limiting Service (Hard)

## Context
A junior developer has written an API rate limiting service to protect the platform from abuse. This handles request throttling, quota management, and API key validation. Review this code and identify all issues.

**Time Limit**: 40 minutes  
**Difficulty**: Hard  
**Expected Issues**: 18+

---

## Code to Review

```java
package com.platform.ratelimit;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Rate limiting controller.
 * Manages API quotas and request throttling.
 */
@RequestMapping("/ratelimit")
@RestController
public class RateLimitController {

    @Autowired
    public ApiKeyRepository apiKeyRepository;

    @Autowired
    public UsageRepository usageRepository;

    public static final Logger logger = LoggerFactory.getLogger(RateLimitController.class);

    // In-memory rate limit tracking
    private static Map<String, Integer> requestCounts = new HashMap<>();
    private static Map<String, Long> windowStart = new HashMap<>();

    // Default limits
    private static final int DEFAULT_RATE_LIMIT = 100;
    private static final long WINDOW_SIZE_MS = 60000;  // 1 minute

    @GetMapping("/check")
    public boolean checkRateLimit(@RequestParam String apiKey,
                                  @RequestParam String endpoint) {

        ApiKey key = apiKeyRepository.findByKey(apiKey);
        
        if (key == null || key.getStatus() != "ACTIVE") {
            return false;
        }

        String trackingKey = apiKey + "-" + endpoint;
        long now = System.currentTimeMillis();

        // Reset window if expired
        if (!windowStart.containsKey(trackingKey) || 
            now - windowStart.get(trackingKey) > WINDOW_SIZE_MS) {
            windowStart.put(trackingKey, now);
            requestCounts.put(trackingKey, 0);
        }

        int count = requestCounts.getOrDefault(trackingKey, 0);
        int limit = key.getRateLimit() != null ? key.getRateLimit() : DEFAULT_RATE_LIMIT;

        if (count < limit) {
            requestCounts.put(trackingKey, count + 1);
            return true;
        }

        return false;
    }

    @PostMapping("/create-key")
    public String createApiKey(@RequestParam String userId,
                              @RequestParam String keyName,
                              @RequestParam int rateLimit,
                              @RequestParam boolean unlimited) {

        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID().toString());
        key.setKey(generateApiKey());
        key.setUserId(userId);
        key.setName(keyName);
        key.setStatus("ACTIVE");
        key.setCreatedAt(new Date());

        if (unlimited) {
            key.setRateLimit(Integer.MAX_VALUE);
        } else {
            key.setRateLimit(rateLimit);
        }

        apiKeyRepository.save(key);

        logger.info("API key created: " + key.getKey() + " for user: " + userId);

        return key.getKey();
    }

    @DeleteMapping("/revoke/{keyId}")
    public void revokeApiKey(@PathVariable String keyId) {
        ApiKey key = apiKeyRepository.findById(keyId).get();
        key.setStatus("REVOKED");
        key.setRevokedAt(new Date());
        apiKeyRepository.save(key);
    }

    @PostMapping("/update-limit")
    public void updateRateLimit(@RequestParam String apiKey,
                               @RequestParam int newLimit,
                               @RequestParam boolean override) {

        ApiKey key = apiKeyRepository.findByKey(apiKey);
        
        if (override || newLimit <= key.getRateLimit()) {
            key.setRateLimit(newLimit);
            apiKeyRepository.save(key);
        }
    }

    @GetMapping("/usage")
    public Map<String, Object> getUsage(@RequestParam String apiKey,
                                       @RequestParam String period) {

        Map<String, Object> usage = new HashMap<>();
        usage.put("apiKey", apiKey);
        usage.put("currentCount", requestCounts.getOrDefault(apiKey, 0));
        usage.put("limit", getLimit(apiKey));
        usage.put("windowReset", windowStart.getOrDefault(apiKey, 0L) + WINDOW_SIZE_MS);

        return usage;
    }

    @PostMapping("/reset-counts")
    public void resetCounts(@RequestParam String apiKey,
                           @RequestParam boolean resetAll) {

        if (resetAll) {
            requestCounts.clear();
            windowStart.clear();
        } else {
            requestCounts.remove(apiKey);
            windowStart.remove(apiKey);
        }
    }

    @GetMapping("/list-keys")
    public List<ApiKey> listApiKeys(@RequestParam String userId,
                                   @RequestParam boolean showAll) {

        if (showAll) {
            return apiKeyRepository.findAll();
        }
        return apiKeyRepository.findByUserId(userId);
    }

    private String generateApiKey() {
        return "ak_" + UUID.randomUUID().toString().replace("-", "");
    }

    private int getLimit(String apiKey) {
        ApiKey key = apiKeyRepository.findByKey(apiKey);
        return key != null ? key.getRateLimit() : DEFAULT_RATE_LIMIT;
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Thread safety, Security, Authorization, Rate limit bypass
3. Consider distributed systems and scalability

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- unlimited, override, resetAll, showAll from client
- Static HashMaps not thread-safe
- Race condition in increment logic
- API key logged in plain text
- rateLimit from client (anyone can set their own limit)
- != for string comparison
- Won't work in distributed/clustered deployment
- No authorization on create/revoke/update
- Integer overflow with MAX_VALUE

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Security Issues

1. **unlimited from client** (Lines 62, 71-72)
   ```java
   @RequestParam boolean unlimited
   if (unlimited) {
       key.setRateLimit(Integer.MAX_VALUE);
   }
   // Anyone can create unlimited API key!
   // Bypasses all rate limiting
   // Fix: Server-side entitlement check, admin-only
   ```

2. **rateLimit from client** (Lines 61, 74)
   ```java
   @RequestParam int rateLimit
   key.setRateLimit(rateLimit);
   // User can set their own rate limit to any value
   // rateLimit=1000000 effectively bypasses limiting
   ```

3. **override from client** (Lines 92, 95)
   ```java
   @RequestParam boolean override
   if (override || newLimit <= key.getRateLimit()) {
       key.setRateLimit(newLimit);
   }
   // Anyone can increase their rate limit!
   ```

4. **resetAll from client** (Lines 111, 113-116)
   ```java
   @RequestParam boolean resetAll
   if (resetAll) {
       requestCounts.clear();
       windowStart.clear();
   }
   // Anyone can reset ALL rate limit counts!
   // DoS attack enabler - everyone's limits reset
   ```

5. **showAll from client** (Lines 123, 125-126)
   ```java
   @RequestParam boolean showAll
   if (showAll) {
       return apiKeyRepository.findAll();
   }
   // Anyone can list ALL users' API keys!
   // Massive security breach
   ```

6. **API key logged** (Line 79)
   ```java
   logger.info("API key created: " + key.getKey() + " for user: " + userId);
   // API keys are secrets - never log them!
   // Fix: Log key ID, not the actual key
   ```

7. **No authorization on key management** (Lines 58-79, 82-88, 89-100)
   ```java
   @PostMapping("/create-key")
   @DeleteMapping("/revoke/{keyId}")
   @PostMapping("/update-limit")
   // Anyone can create keys for any userId
   // Anyone can revoke any key
   // Anyone can update any key's limit
   ```

### 🔴 CRITICAL Concurrency Issues

8. **Static HashMap not thread-safe** (Lines 23-24)
   ```java
   private static Map<String, Integer> requestCounts = new HashMap<>();
   private static Map<String, Long> windowStart = new HashMap<>();
   // HashMap corrupts under concurrent access
   // Fix: ConcurrentHashMap, or proper synchronization
   ```

9. **Race condition in rate limiting** (Lines 42-53)
   ```java
   int count = requestCounts.getOrDefault(trackingKey, 0);
   if (count < limit) {
       requestCounts.put(trackingKey, count + 1);
       return true;
   }
   // Thread 1: reads count = 99
   // Thread 2: reads count = 99
   // Both increment to 100
   // Both pass check even though limit is 100
   // Fix: Use AtomicInteger, or ConcurrentHashMap.compute()
   ```

10. **Won't work in distributed system** (Lines 23-24)
    ```java
    private static Map<String, Integer> requestCounts
    // Static in-memory state not shared across instances
    // Load balancer sends requests to different servers
    // User gets 100 requests per server = 100 * N total
    // Fix: Use Redis or distributed cache
    ```

### 🔴 MAJOR Issues

11. **String comparison with !=** (Line 36)
    ```java
    if (key == null || key.getStatus() != "ACTIVE") {
    // != compares references, not values
    // Will always fail even for active keys
    // Fix: !"ACTIVE".equals(key.getStatus())
    ```

12. **Integer overflow** (Line 72)
    ```java
    key.setRateLimit(Integer.MAX_VALUE);
    // If any calculation adds to this: overflow to negative
    // count + 1 could overflow
    ```

13. **No response body for several endpoints** (Lines 82, 89, 109)
    ```java
    public void revokeApiKey(...)
    public void updateRateLimit(...)
    public void resetCounts(...)
    ```

14. **Optional.get() without check** (Line 84)
    ```java
    apiKeyRepository.findById(keyId).get()
    ```

15. **API key returned in response** (Lines 58, 79)
    ```java
    public String createApiKey(...)
    return key.getKey();
    // Key returned in plain text over HTTP
    // Should be shown once, then only display masked version
    ```

16. **API key in request params** (Lines 31, 91, 102, 111)
    ```java
    @RequestParam String apiKey
    // API key in URL gets logged in access logs
    // Should be in header: Authorization: Bearer <key>
    ```

### 🟡 MEDIUM Issues

17. **Field injection** (Lines 15-19)
    ```java
    @Autowired public ApiKeyRepository
    ```

18. **java.util.Date** (Lines 70, 86)
    ```java
    key.setCreatedAt(new Date());
    ```

19. **Usage doesn't account for endpoint** (Lines 102-110)
    ```java
    requestCounts.getOrDefault(apiKey, 0)
    // Tracking key is apiKey + endpoint, but usage lookup uses just apiKey
    // Returns wrong count
    ```

20. **No validation on inputs**
    - keyName could be empty
    - newLimit could be negative
    - period param unused

21. **List returns entities** (Lines 121-128)
    ```java
    public List<ApiKey> listApiKeys(...)
    // Returns full ApiKey including the actual key value!
    // Should return DTO with masked key
    ```

### 🟢 MINOR Issues

22. **Hardcoded limits** (Lines 27-28)
    ```java
    private static final int DEFAULT_RATE_LIMIT = 100;
    private static final long WINDOW_SIZE_MS = 60000;
    // Should be configurable properties
    ```

23. **No metrics/monitoring**
    - Should track rate limit hits, near-limit warnings
    - Alert on sustained high usage

24. **Window reset is all-or-nothing**
    - Fixed window can have burst at window boundary
    - Should use sliding window or token bucket

</details>

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Rate Limiting Controller

```java
package com.platform.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * API Key and Rate Limiting Controller.
 * Uses Redis for distributed rate limiting.
 */
@RestController
@RequestMapping("/api/v1/rate-limit")
public class RateLimitController {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitController.class);

    @Value("${ratelimit.default:100}")
    private int defaultRateLimit;

    @Value("${ratelimit.window.seconds:60}")
    private int windowSeconds;

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitService rateLimitService;  // FIX: Uses Redis

    // Constructor injection...

    /**
     * Check rate limit for incoming request.
     * API key passed in header, NOT query params.
     */
    @GetMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(
            @RequestHeader("X-API-Key") String apiKey,  // FIX: Header, not query param
            @RequestParam String endpoint) {

        // FIX: Validate API key from database
        ApiKey key = apiKeyRepository.findByKeyHash(hashKey(apiKey))
            .orElseThrow(() -> new InvalidApiKeyException("Invalid API key"));

        // FIX: Use .equals() for string comparison
        if (!"ACTIVE".equals(key.getStatus())) {
            throw new InvalidApiKeyException("API key is not active");
        }

        String trackingKey = key.getId() + ":" + endpoint;
        int limit = key.getRateLimit();

        // FIX: Atomic rate limiting with Redis
        RateLimitResult result = rateLimitService.checkAndIncrement(trackingKey, limit, windowSeconds);

        if (!result.isAllowed()) {
            logger.warn("Rate limit exceeded - keyId: {}, endpoint: {}", key.getId(), endpoint);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(limit))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(result.getResetTime()))
                .body(new RateLimitResponse(false, 0, result.getResetTime()));
        }

        return ResponseEntity.ok()
            .header("X-RateLimit-Limit", String.valueOf(limit))
            .header("X-RateLimit-Remaining", String.valueOf(result.getRemaining()))
            .header("X-RateLimit-Reset", String.valueOf(result.getResetTime()))
            .body(new RateLimitResponse(true, result.getRemaining(), result.getResetTime()));
    }

    @PostMapping("/keys")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<ApiKeyResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        // Generate secure API key
        String rawKey = generateSecureKey();
        String keyHash = hashKey(rawKey);

        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID().toString());
        key.setKeyHash(keyHash);  // FIX: Store hash, not plain key
        key.setKeyPrefix(rawKey.substring(0, 8));  // For display only
        key.setName(request.getKeyName());
        key.setUserId(currentUserId);
        key.setRateLimit(defaultRateLimit);
        key.setStatus("ACTIVE");
        key.setCreatedAt(Instant.now());

        apiKeyRepository.save(key);

        // FIX: Log key ID, never the actual key
        logger.info("API key created - id: {}, userId: {}", key.getId(), currentUserId);

        // FIX: Return key ONCE only (never stored in plain text)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new ApiKeyResponse(
                key.getId(),
                rawKey,  // Only time the full key is returned
                key.getName(),
                key.getRateLimit(),
                "IMPORTANT: Store this key securely. It cannot be retrieved again."
            )
        );
    }

    @DeleteMapping("/keys/{keyId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> revokeApiKey(@PathVariable String keyId) {

        String currentUserId = SecurityContext.getCurrentUserId();

        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ApiKeyNotFoundException(keyId));

        // FIX: Authorization - can only revoke own keys
        if (!Objects.equals(key.getUserId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized to revoke this key");
        }

        key.setStatus("REVOKED");
        key.setRevokedAt(Instant.now());
        apiKeyRepository.save(key);

        logger.info("API key revoked - id: {}, by: {}", keyId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/keys/{keyId}/limit")
    @PreAuthorize("hasRole('ADMIN')")  // FIX: Only admin can change limits
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<ApiKeyResponse> updateRateLimit(
            @PathVariable String keyId,
            @Valid @RequestBody UpdateLimitRequest request) {

        String adminUserId = SecurityContext.getCurrentUserId();

        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ApiKeyNotFoundException(keyId));

        // FIX: Validate limit is reasonable
        if (request.getNewLimit() <= 0 || request.getNewLimit() > 10000) {
            throw new InvalidLimitException("Limit must be between 1 and 10000");
        }

        int oldLimit = key.getRateLimit();
        key.setRateLimit(request.getNewLimit());
        apiKeyRepository.save(key);

        logger.info("Rate limit updated - keyId: {}, from: {} to: {}, by: {}", 
                   keyId, oldLimit, request.getNewLimit(), adminUserId);

        return ResponseEntity.ok(new ApiKeyResponse(
            key.getId(),
            null,  // Never return the key again
            key.getName(),
            key.getRateLimit(),
            null
        ));
    }

    @GetMapping("/keys/{keyId}/usage")
    public ResponseEntity<UsageResponse> getUsage(@PathVariable String keyId) {

        String currentUserId = SecurityContext.getCurrentUserId();

        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ApiKeyNotFoundException(keyId));

        // FIX: Authorization
        if (!Objects.equals(key.getUserId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized");
        }

        // FIX: Get usage from Redis (tracking key is keyId:*)
        Map<String, Long> usageByEndpoint = rateLimitService.getUsageStats(key.getId());

        return ResponseEntity.ok(new UsageResponse(
            key.getId(),
            key.getRateLimit(),
            usageByEndpoint,
            Instant.now()
        ));
    }

    @GetMapping("/keys")
    public ResponseEntity<List<ApiKeyListResponse>> listApiKeys() {

        String currentUserId = SecurityContext.getCurrentUserId();

        // FIX: Only return current user's keys
        List<ApiKey> keys = apiKeyRepository.findByUserId(currentUserId);

        // FIX: Return DTO with masked key
        List<ApiKeyListResponse> response = keys.stream()
            .map(k -> new ApiKeyListResponse(
                k.getId(),
                k.getKeyPrefix() + "..." + "*".repeat(24),  // Masked
                k.getName(),
                k.getRateLimit(),
                k.getStatus(),
                k.getCreatedAt()
            ))
            .toList();

        return ResponseEntity.ok(response);
    }

    // FIX: REMOVED @PostMapping("/reset") - no public reset capability

    private String generateSecureKey() {
        // Generate cryptographically secure API key
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String hashKey(String rawKey) {
        // Use secure hashing (SHA-256 or bcrypt)
        return EncryptionUtils.hash(rawKey);
    }
}
```

### Redis-based Rate Limit Service

```java
@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Atomic rate limit check using Redis INCR with expiry.
     * Thread-safe and works in distributed environment.
     */
    public RateLimitResult checkAndIncrement(String key, int limit, int windowSeconds) {
        String redisKey = "ratelimit:" + key;

        // Atomic increment
        Long count = redisTemplate.opsForValue().increment(redisKey);
        
        if (count == 1) {
            // First request in window - set expiry
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        long resetTime = Instant.now().plusSeconds(ttl != null ? ttl : windowSeconds).getEpochSecond();

        boolean allowed = count <= limit;
        int remaining = allowed ? limit - count.intValue() : 0;

        return new RateLimitResult(allowed, remaining, resetTime);
    }

    public Map<String, Long> getUsageStats(String keyId) {
        // Get all keys matching pattern
        Set<String> keys = redisTemplate.keys("ratelimit:" + keyId + ":*");
        Map<String, Long> stats = new HashMap<>();
        
        for (String key : keys) {
            String endpoint = key.substring(key.lastIndexOf(":") + 1);
            String value = redisTemplate.opsForValue().get(key);
            stats.put(endpoint, value != null ? Long.parseLong(value) : 0);
        }
        
        return stats;
    }
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| `unlimitedAccess` | Client parameter | REMOVED |
| `isAdmin` for reset | Client parameter | REMOVED reset endpoint |
| `showAll` for list | Client parameter | Only user's own keys |
| API key in URL | Query param | `X-API-Key` header |
| API key logged | Plain text in logs | Only log key ID |
| Thread safety | HashMap | Redis (distributed & atomic) |
| Race condition | Check-then-increment | Atomic INCR in Redis |
| Distributed | Static in-memory | Redis shared across instances |
| String comparison | `!=` | `.equals()` |
| Key storage | Plain text | Store hash only |
| Key in response | Full key in list | Masked prefix |
| Authorization | None | Check userId on all operations |

### Redis Rate Limit Flow

```
Request comes in with X-API-Key header
        │
        ▼
┌───────────────────────────┐
│ 1. Lookup key by hash     │
│ 2. Validate key is ACTIVE │
└───────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│ Redis: INCR ratelimit:{keyId}:{endpoint}  │
│        (Atomic - no race condition)       │
│ If first request: EXPIRE key {window}s   │
└───────────────────────────────────────────┘
        │
        ▼
count <= limit? ──No──▶ 429 Too Many Requests
        │
       Yes
        │
        ▼
    200 OK (with rate limit headers)
```

</details>

