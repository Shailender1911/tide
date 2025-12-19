# Practice Problem 11: Report Generation API (Medium)

## Context
A junior developer has written a report generation API for a business analytics platform. Users can generate financial reports, export data, and schedule periodic reports. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.analytics.reports;

import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Report generation controller.
 * Supports on-demand and scheduled report generation.
 */
@RequestMapping("/reports")
@RestController
public class ReportController {

    @Autowired
    public ReportRepository reportRepository;

    @Autowired
    public TransactionRepository transactionRepository;

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public ReportGenerator reportGenerator;

    public static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    // Store generated reports in memory for quick access
    private static Map<String, byte[]> reportCache = new HashMap<>();

    @GetMapping("/generate")
    public byte[] generateReport(@RequestParam String userId,
                                @RequestParam String reportType,
                                @RequestParam String startDate,
                                @RequestParam String endDate,
                                @RequestParam boolean includeDetails) {

        User user = userRepository.findById(userId).get();

        List<Transaction> transactions = transactionRepository
            .findByUserIdAndDateBetween(userId, startDate, endDate);

        byte[] reportData;

        if (reportType.equals("PDF")) {
            reportData = reportGenerator.generatePdf(user, transactions, includeDetails);
        } else if (reportType.equals("CSV")) {
            reportData = reportGenerator.generateCsv(user, transactions, includeDetails);
        } else {
            reportData = reportGenerator.generateExcel(user, transactions, includeDetails);
        }

        // Cache for quick access
        String cacheKey = userId + "-" + reportType + "-" + startDate + "-" + endDate;
        reportCache.put(cacheKey, reportData);

        logger.info("Report generated for user: " + userId + " from " + startDate + " to " + endDate);

        return reportData;
    }

    @GetMapping("/cached/{cacheKey}")
    public byte[] getCachedReport(@PathVariable String cacheKey) {
        return reportCache.get(cacheKey);
    }

    @PostMapping("/schedule")
    public void scheduleReport(@RequestParam String userId,
                              @RequestParam String reportType,
                              @RequestParam String frequency,
                              @RequestParam String emailTo,
                              @RequestParam boolean sendToAll) {

        ScheduledReport sr = new ScheduledReport();
        sr.setId(new Random().nextInt(100000));
        sr.setUserId(userId);
        sr.setReportType(reportType);
        sr.setFrequency(frequency);
        sr.setStatus("ACTIVE");

        if (sendToAll) {
            sr.setRecipients(getAllCompanyEmails());
        } else {
            sr.setRecipients(Arrays.asList(emailTo));
        }

        reportRepository.save(sr);
    }

    @GetMapping("/download-all")
    public byte[] downloadAllReports(@RequestParam String userId,
                                    @RequestParam boolean adminExport) throws IOException {

        List<Report> reports;
        
        if (adminExport) {
            reports = reportRepository.findAll();
        } else {
            reports = reportRepository.findByUserId(userId);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (Report report : reports) {
            ZipEntry entry = new ZipEntry(report.getFilename());
            zos.putNextEntry(entry);
            zos.write(report.getData());
            zos.closeEntry();
        }

        zos.close();
        return baos.toByteArray();
    }

    @DeleteMapping("/{reportId}")
    public void deleteReport(@PathVariable String reportId,
                            @RequestParam boolean hardDelete) {

        Report report = reportRepository.findById(reportId).get();

        if (hardDelete) {
            reportRepository.delete(report);
            // Also delete from filesystem
            new File("/reports/" + report.getFilename()).delete();
        } else {
            report.setStatus("DELETED");
            reportRepository.save(report);
        }
    }

    private List<String> getAllCompanyEmails() {
        return userRepository.findAll().stream()
            .map(User::getEmail)
            .toList();
    }

    @GetMapping("/audit-log")
    public List<AuditEntry> getAuditLog(@RequestParam String startDate,
                                        @RequestParam String endDate,
                                        @RequestParam boolean showAllUsers) {

        if (showAllUsers) {
            return auditRepository.findByDateBetween(startDate, endDate);
        }
        return auditRepository.findByUserIdAndDateBetween(
            getCurrentUserId(), startDate, endDate);
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Authorization, Data exposure, Resource management, Memory
3. Consider reporting/analytics security best practices

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- adminExport, hardDelete, sendToAll, showAllUsers from client
- Static cache stores report data (memory issues, data exposure)
- Cache key can be guessed to access others' reports
- GET for generating reports (should be POST)
- No authorization on most endpoints
- Stream not closed properly
- Random ID for scheduled reports
- String dates not validated
- findAll() without pagination

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Security Issues

1. **adminExport from client** (Lines 86, 89-93)
   ```java
   @RequestParam boolean adminExport
   if (adminExport) {
       reports = reportRepository.findAll();
   }
   // Anyone can export ALL users' reports by setting adminExport=true
   // Massive data breach potential
   // Fix: @PreAuthorize("hasRole('ADMIN')")
   ```

2. **hardDelete from client** (Lines 107, 111-115)
   ```java
   @RequestParam boolean hardDelete
   if (hardDelete) {
       reportRepository.delete(report);
   }
   // Anyone can permanently delete any report
   // No ownership check either
   ```

3. **sendToAll from client** (Lines 71, 77-79)
   ```java
   @RequestParam boolean sendToAll
   if (sendToAll) {
       sr.setRecipients(getAllCompanyEmails());
   }
   // Anyone can schedule reports to be sent to ALL employees
   // Spam + data exposure risk
   ```

4. **showAllUsers from client** (Lines 128, 130-134)
   ```java
   @RequestParam boolean showAllUsers
   if (showAllUsers) {
       return auditRepository.findByDateBetween(...);
   }
   // Anyone can view audit logs of all users
   // Security/compliance violation
   ```

5. **Cache key predictable** (Lines 52-53, 58-60)
   ```java
   String cacheKey = userId + "-" + reportType + "-" + startDate + "-" + endDate;
   reportCache.put(cacheKey, reportData);
   
   @GetMapping("/cached/{cacheKey}")
   public byte[] getCachedReport(@PathVariable String cacheKey)
   // Attacker can guess cache keys: "user123-PDF-2024-01-01-2024-01-31"
   // Access other users' reports!
   // Fix: Use UUID, verify ownership before returning
   ```

6. **No authorization on generate/download** (Lines 30-55, 85-103)
   ```java
   @GetMapping("/generate")
   @GetMapping("/download-all")
   // User A can generate reports for User B by providing their userId
   ```

### 🔴 MAJOR Issues

7. **Static cache with unbounded growth** (Line 28)
   ```java
   private static Map<String, byte[]> reportCache = new HashMap<>();
   // Not thread-safe
   // Unbounded memory growth - OutOfMemoryError
   // Reports can be large (MBs)
   // Lost on restart
   // Fix: Use proper cache (Redis, Caffeine) with TTL and size limits
   ```

8. **GET for report generation** (Line 30)
   ```java
   @GetMapping("/generate")
   // GET should be idempotent - generating report is not
   // URL with dates gets logged in access logs
   // Fix: Use POST
   ```

9. **No response headers for downloads** (Lines 30, 58, 85)
   ```java
   public byte[] generateReport(...)
   public byte[] getCachedReport(...)
   public byte[] downloadAllReports(...)
   // No Content-Type, Content-Disposition headers
   // Browser won't know how to handle the file
   ```

10. **Random ID for scheduled report** (Line 73)
    ```java
    sr.setId(new Random().nextInt(100000));
    // Collision risk, predictable
    // Fix: UUID
    ```

11. **Optional.get() without check** (Lines 37, 109)
    ```java
    userRepository.findById(userId).get()
    reportRepository.findById(reportId).get()
    ```

12. **ZipOutputStream not in try-with-resources** (Lines 96-101)
    ```java
    ZipOutputStream zos = new ZipOutputStream(baos);
    // ... operations ...
    zos.close();
    // If exception occurs, stream not closed
    // Fix: try (ZipOutputStream zos = ...) { }
    ```

13. **File path manipulation risk** (Line 114)
    ```java
    new File("/reports/" + report.getFilename()).delete();
    // If filename is "../../../etc/passwd" - path traversal
    // File.delete() returns boolean - not checked
    ```

### 🟡 MEDIUM Issues

14. **Field injection** (Lines 15-25)
    ```java
    @Autowired public ReportRepository
    ```

15. **String dates not validated** (Lines 33-34, 129-130)
    ```java
    @RequestParam String startDate,
    @RequestParam String endDate
    // No validation of format
    // Should use LocalDate with @DateTimeFormat
    ```

16. **findAll() without pagination** (Lines 91, 121)
    ```java
    reportRepository.findAll()
    userRepository.findAll()
    // Could be millions of records - memory exhaustion
    ```

17. **No void response** (Lines 65, 105)
    ```java
    public void scheduleReport(...)
    public void deleteReport(...)
    // No confirmation or ID returned
    ```

18. **Logger info not useful** (Line 55)
    ```java
    logger.info("Report generated for user: " + userId + "...");
    // Should include report ID, size, type
    ```

19. **auditRepository not declared** (Lines 131, 133)
    ```java
    auditRepository.findByDateBetween(...)
    // Variable used but never declared/autowired
    // Code won't compile!
    ```

### 🟢 MINOR Issues

20. **getCurrentUserId() not defined** (Line 134)
    ```java
    getCurrentUserId()
    // Method called but not defined
    ```

21. **Default report type is Excel** (Lines 44-48)
    ```java
    } else {
        reportData = reportGenerator.generateExcel(...);
    }
    // Undocumented default behavior
    ```

22. **No timeout for report generation**
    - Large date ranges could take forever

</details>

