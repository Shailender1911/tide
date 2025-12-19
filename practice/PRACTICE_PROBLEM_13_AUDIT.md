# Practice Problem 13: Audit Logging API (Medium)

## Context
A junior developer has written an audit logging service for compliance requirements. This tracks all sensitive operations and provides search/export capabilities. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.bank.audit;

import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Audit log controller for compliance.
 * Tracks all sensitive operations in the system.
 */
@RequestMapping("/audit")
@RestController
public class AuditController {

    @Autowired
    public AuditRepository auditRepository;

    @Autowired
    public UserRepository userRepository;

    public static final Logger logger = LoggerFactory.getLogger(AuditController.class);

    @PostMapping("/log")
    public void createAuditLog(@RequestParam String userId,
                               @RequestParam String action,
                               @RequestParam String resourceType,
                               @RequestParam String resourceId,
                               @RequestParam String details,
                               @RequestParam String ipAddress) {

        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        entry.setTimestamp(new Date());

        auditRepository.save(entry);
    }

    @GetMapping("/search")
    public List<AuditEntry> searchLogs(@RequestParam String userId,
                                       @RequestParam String startDate,
                                       @RequestParam String endDate,
                                       @RequestParam String action,
                                       @RequestParam boolean includeDeleted) {

        List<AuditEntry> results = auditRepository.findByUserIdAndDateBetween(
            userId, startDate, endDate);

        if (!includeDeleted) {
            results = results.stream()
                .filter(e -> !e.isDeleted())
                .toList();
        }

        if (action != null && !action.isEmpty()) {
            results = results.stream()
                .filter(e -> e.getAction().equals(action))
                .toList();
        }

        return results;
    }

    @GetMapping("/export")
    public byte[] exportLogs(@RequestParam String format,
                            @RequestParam String startDate,
                            @RequestParam String endDate,
                            @RequestParam boolean allUsers) throws IOException {

        List<AuditEntry> logs;

        if (allUsers) {
            logs = auditRepository.findByDateBetween(startDate, endDate);
        } else {
            logs = auditRepository.findByUserIdAndDateBetween(
                getCurrentUserId(), startDate, endDate);
        }

        if (format.equals("CSV")) {
            return generateCsv(logs);
        } else {
            return generateJson(logs);
        }
    }

    @DeleteMapping("/{entryId}")
    public void deleteAuditEntry(@PathVariable String entryId,
                                @RequestParam String reason,
                                @RequestParam boolean permanent) {

        AuditEntry entry = auditRepository.findById(entryId).get();

        if (permanent) {
            auditRepository.delete(entry);
        } else {
            entry.setDeleted(true);
            entry.setDeletedReason(reason);
            entry.setDeletedAt(new Date());
            auditRepository.save(entry);
        }
    }

    @PutMapping("/modify/{entryId}")
    public void modifyAuditEntry(@PathVariable String entryId,
                                @RequestParam String newDetails,
                                @RequestParam String modifyReason) {

        AuditEntry entry = auditRepository.findById(entryId).get();
        entry.setDetails(newDetails);
        entry.setModifiedReason(modifyReason);
        entry.setModifiedAt(new Date());
        auditRepository.save(entry);
    }

    @PostMapping("/bulk-delete")
    public void bulkDelete(@RequestBody List<String> entryIds,
                          @RequestParam boolean bypassRetention) {

        for (String id : entryIds) {
            AuditEntry entry = auditRepository.findById(id).get();
            
            if (bypassRetention || canDelete(entry)) {
                auditRepository.delete(entry);
            }
        }
    }

    private boolean canDelete(AuditEntry entry) {
        // Check retention period
        long ageInDays = (System.currentTimeMillis() - entry.getTimestamp().getTime()) 
            / (24 * 60 * 60 * 1000);
        return ageInDays > 365;  // 1 year retention
    }

    private byte[] generateCsv(List<AuditEntry> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,userId,action,resourceType,resourceId,timestamp,details\n");
        for (AuditEntry log : logs) {
            sb.append(log.getId()).append(",")
              .append(log.getUserId()).append(",")
              .append(log.getAction()).append(",")
              .append(log.getResourceType()).append(",")
              .append(log.getResourceId()).append(",")
              .append(log.getTimestamp()).append(",")
              .append(log.getDetails()).append("\n");
        }
        return sb.toString().getBytes();
    }

    private byte[] generateJson(List<AuditEntry> logs) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsBytes(logs);
    }

    private String getCurrentUserId() {
        return SecurityContext.getCurrentUser().getId();
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Audit integrity, Compliance, Authorization, Data immutability
3. Consider audit log security best practices

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Audit logs should be IMMUTABLE - no modify/delete!
- allUsers, permanent, bypassRetention from client
- includeDeleted exposes soft-deleted data
- ipAddress from client (can be spoofed)
- No authorization on any endpoint
- CSV injection vulnerability
- Export all users' logs
- No retention enforcement on export

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Compliance Issues

1. **Audit logs can be MODIFIED** (Lines 100-109)
   ```java
   @PutMapping("/modify/{entryId}")
   public void modifyAuditEntry(@PathVariable String entryId,
                               @RequestParam String newDetails, ...)
   // CRITICAL: Audit logs must be IMMUTABLE
   // Modifying audit logs destroys their legal/compliance value
   // Evidence tampering!
   // Fix: REMOVE this endpoint entirely
   ```

2. **Audit logs can be DELETED** (Lines 85-97, 111-122)
   ```java
   @DeleteMapping("/{entryId}")
   @PostMapping("/bulk-delete")
   // Audit logs should NEVER be deleted
   // Regulatory requirement to retain for years
   // Even soft-delete is questionable
   // Fix: Remove delete endpoints, archive old logs
   ```

3. **bypassRetention from client** (Lines 112, 117)
   ```java
   @RequestParam boolean bypassRetention
   if (bypassRetention || canDelete(entry)) {
   // Anyone can bypass retention policy!
   // Regulatory violation
   ```

4. **permanent delete from client** (Lines 87, 90-92)
   ```java
   @RequestParam boolean permanent
   if (permanent) {
       auditRepository.delete(entry);
   }
   // Permanent deletion of audit records!
   ```

5. **allUsers from client** (Lines 67, 70-71)
   ```java
   @RequestParam boolean allUsers
   if (allUsers) {
       logs = auditRepository.findByDateBetween(...);
   }
   // Anyone can export ALL users' audit logs
   // Massive data exposure
   ```

6. **No authorization on ANY endpoint**
   ```java
   @GetMapping("/search")
   @GetMapping("/export")
   @DeleteMapping("/{entryId}")
   @PutMapping("/modify/{entryId}")
   // Anyone can search, export, delete, modify audit logs
   // Should be restricted to compliance/admin roles
   ```

### 🔴 MAJOR Issues

7. **ipAddress from client input** (Lines 29, 38)
   ```java
   @RequestParam String ipAddress
   entry.setIpAddress(ipAddress);
   // Client can spoof IP address
   // Fix: Get from request: request.getRemoteAddr()
   // Or better: X-Forwarded-For with trusted proxy
   ```

8. **Audit log creation as public endpoint** (Lines 23-42)
   ```java
   @PostMapping("/log")
   public void createAuditLog(...)
   // Anyone can create fake audit entries!
   // Should be internal service, not public API
   ```

9. **includeDeleted exposes soft-deleted data** (Lines 47, 51-55)
   ```java
   @RequestParam boolean includeDeleted
   if (!includeDeleted) {
       results = results.stream().filter(...)
   }
   // Anyone can see "deleted" records
   // Bypasses deletion intent
   ```

10. **CSV injection vulnerability** (Lines 131-143)
    ```java
    sb.append(log.getDetails()).append("\n");
    // If details contains: =CMD|' /C calc'!A0
    // Opening CSV in Excel executes command!
    // Fix: Escape or sanitize fields starting with =, +, -, @
    ```

11. **Optional.get() without check** (Lines 89, 104, 115)
    ```java
    auditRepository.findById(entryId).get()
    ```

### 🟡 MEDIUM Issues

12. **Field injection** (Lines 15-19)
    ```java
    @Autowired public AuditRepository
    ```

13. **No pagination on search/export** (Lines 43-62, 64-82)
    ```java
    List<AuditEntry> results = auditRepository.findByUserIdAndDateBetween(...)
    // Could return millions of records
    // Memory exhaustion
    ```

14. **String dates not validated** (Lines 45-46, 66-67)
    ```java
    @RequestParam String startDate,
    @RequestParam String endDate
    // No format validation
    ```

15. **No response headers on export** (Lines 64-82)
    ```java
    public byte[] exportLogs(...)
    // No Content-Type or Content-Disposition
    ```

16. **java.util.Date** (Lines 39, 95, 107)
    ```java
    entry.setTimestamp(new Date());
    ```

17. **void returns for state changes** (Lines 23, 85, 100, 111)
    - No confirmation returned

### 🟢 MINOR Issues

18. **userId as param in log creation** (Lines 24, 33)
    ```java
    @RequestParam String userId
    entry.setUserId(userId);
    // Should be taken from security context
    // User could log actions as someone else
    ```

19. **No validation on action/resourceType** (Lines 25-26)
    ```java
    @RequestParam String action,
    @RequestParam String resourceType
    // Should validate against allowed values
    ```

20. **Filter in memory vs database** (Lines 51-62)
    ```java
    results = results.stream().filter(...).toList();
    // Filtering in memory after fetching all
    // Should filter in database query
    ```

21. **Export has no audit trail**
    - Exporting audit logs should itself be audited
    - Who exported what and when?

22. **canDelete uses magic number** (Lines 124-128)
    ```java
    return ageInDays > 365;
    // Should be configurable constant
    ```

### Compliance Best Practices Violations

- Audit logs should be write-once, read-many (WORM)
- Should have tamper-evident hashing
- Should be in separate database with restricted access
- Should have mandatory retention periods
- Access to audit logs should be audited

</details>

