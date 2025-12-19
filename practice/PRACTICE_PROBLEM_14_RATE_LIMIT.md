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

