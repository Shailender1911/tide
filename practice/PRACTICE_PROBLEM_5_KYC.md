# Practice Problem 5: KYC Verification API (Medium)

## Context
A junior developer has written a KYC (Know Your Customer) verification endpoint for a banking application. This handles identity document verification and approval. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.bank.kyc;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * KYC verification controller. Added manual override for VIP customers
 * who need faster onboarding without document verification.
 */
@RequestMapping("/kyc")
@RestController
public class KycController {

    @Autowired
    public DocumentRepository documentRepository;

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public VerificationService verificationService;

    @Autowired
    public StorageService storageService;

    public static final Logger logger = LoggerFactory.getLogger(KycController.class);

    @PostMapping("/verify/{userId}")
    public void verifyIdentity(@PathVariable String userId,
                               @RequestParam String documentType,
                               @RequestParam String documentNumber,
                               @RequestParam String documentImage,
                               @RequestParam boolean skipVerification) {

        User user = userRepository.findById(userId).get();

        if (skipVerification) {
            user.setKycStatus("VERIFIED");
            user.setKycVerifiedAt(new Date());
            userRepository.save(user);
            return;
        }

        // Store document
        Document doc = new Document();
        doc.setId(UUID.randomUUID().toString());
        doc.setUserId(userId);
        doc.setType(documentType);
        doc.setNumber(documentNumber);
        doc.setImageData(documentImage);
        doc.setCreatedAt(new Date());
        documentRepository.save(doc);

        // Verify with external service
        boolean isValid = verificationService.verifyDocument(documentType, documentNumber);

        if (isValid) {
            user.setKycStatus("VERIFIED");
            user.setKycVerifiedAt(new Date());
        } else {
            user.setKycStatus("REJECTED");
        }
        userRepository.save(user);

        System.out.println("KYC processed for user: " + userId + " Document: " + documentNumber);
    }

    @GetMapping("/status")
    public String getKycStatus(@RequestParam String odUserId) {
        User user = userRepository.findById(odUserId).get();
        return user.getKycStatus() + "|" + user.getDocumentNumber();
    }

    @PutMapping("/override/{userId}")
    public void manualOverride(@PathVariable String userId,
                               @RequestParam String status,
                               @RequestParam String reason,
                               @RequestParam boolean isCompliance) {

        if (!isCompliance) {
            throw new RuntimeException("Not authorized");
        }

        User user = userRepository.findById(userId).get();
        user.setKycStatus(status);
        user.setKycOverrideReason(reason);
        user.setKycVerifiedAt(new Date());
        userRepository.save(user);
    }

    @DeleteMapping("/documents/{userId}")
    public void deleteUserDocuments(@PathVariable String userId) {
        List<Document> docs = documentRepository.findByUserId(userId);
        for (Document doc : docs) {
            documentRepository.delete(doc);
        }
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Compliance, Security, PII handling, Authorization
3. Consider regulatory requirements (GDPR, AML)

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Authorization bypass patterns (skipVerification, isCompliance)
- PII (Personally Identifiable Information) handling
- Document number/image in logs and responses
- Audit trail for compliance actions
- GDPR - right to deletion audit
- Input validation for document types
- Status values validation
- Transaction management
- Response bodies

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Issues

1. **skipVerification from client** (Lines 33, 36)
   ```java
   @RequestParam boolean skipVerification
   if (skipVerification) { user.setKycStatus("VERIFIED"); }
   // Anyone can bypass KYC! Massive compliance violation
   // Fix: Remove this parameter entirely or use server-side role check
   ```

2. **isCompliance from client** (Lines 73, 76)
   ```java
   @RequestParam boolean isCompliance
   if (!isCompliance) { throw... }
   // Another authorization bypass - anyone can override KYC status
   // Fix: @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
   ```

3. **Document number exposed in response** (Line 67)
   ```java
   return user.getKycStatus() + "|" + user.getDocumentNumber();
   // PII exposure! Document numbers (passport, ID) should never be in responses
   // Fix: Return only status, mask document numbers
   ```

4. **Document number logged** (Line 61)
   ```java
   System.out.println("... Document: " + documentNumber);
   // PII in logs - compliance violation (GDPR, PCI)
   // Fix: Never log document numbers, use masked version
   ```

5. **Document image stored as string** (Lines 32, 47)
   ```java
   @RequestParam String documentImage
   doc.setImageData(documentImage);
   // Base64 in database? Images should be in secure storage (S3)
   // Also: No encryption of sensitive document images
   ```

### 🔴 MAJOR Issues

6. **No response bodies** (All endpoints return void/String)
   ```java
   public void verifyIdentity(...)
   // Client gets no confirmation, document ID, verification status
   ```

7. **No audit logging for KYC override** (Lines 72-82)
   ```java
   // KYC override is a compliance-critical action
   // Must log: who did it, when, why, before/after status
   // Required for regulatory audits
   ```

8. **No audit for document deletion** (Lines 84-89)
   ```java
   // GDPR requires audit trail for data deletion
   // Must log: who requested, when, what was deleted
   ```

9. **Optional.get() without check** (Lines 35, 65, 79)
   ```java
   userRepository.findById(userId).get()
   // NPE if user not found
   // Fix: orElseThrow(() -> new UserNotFoundException(...))
   ```

10. **No transaction management** (Lines 44-59)
    ```java
    documentRepository.save(doc);
    // ... verification ...
    userRepository.save(user);
    // If user save fails, orphan document in DB
    ```

11. **No input validation** 
    - documentType not validated against allowed types
    - status in override not validated
    - documentNumber format not validated

12. **GET exposes PII** (Lines 63-67)
    ```java
    @GetMapping("/status")
    @RequestParam String odUserId
    // GET with sensitive user ID in query params - gets logged in access logs
    // Should be POST with body or use path parameter with auth check
    ```

### 🟡 MEDIUM Issues

13. **RuntimeException for auth failure** (Line 77)
    ```java
    throw new RuntimeException("Not authorized");
    // Should be 403 Forbidden with proper exception
    ```

14. **Field injection** (Lines 14-24)
    ```java
    @Autowired public DocumentRepository
    // Constructor injection preferred
    ```

15. **java.util.Date** (Lines 38, 50, 56, 81)
    ```java
    new Date()
    // Use Instant or LocalDateTime
    ```

16. **Logger not used** (Line 26)
    - Logger declared but System.out.println used

17. **No authorization check on status endpoint** (Lines 63-67)
    ```java
    @GetMapping("/status")
    // Anyone can check anyone's KYC status?
    // Should verify requestor owns this account or has permission
    ```

### 🟢 MINOR Issues

18. **Typo in parameter name** (Line 64)
    ```java
    @RequestParam String odUserId  // "od" typo?
    ```

19. **String return for status** (Line 64)
    ```java
    return user.getKycStatus() + "|" + user.getDocumentNumber();
    // Should be proper DTO with JSON structure
    ```

20. **No soft delete for documents** (Lines 84-89)
    ```java
    documentRepository.delete(doc);
    // For compliance, might need soft delete with retention period
    ```

### Compliance-Specific Issues

21. **No verification of document ownership**
    - User A could submit documents for User B

22. **No rate limiting on verification attempts**
    - Could be abused for document number validation attacks

23. **No encryption at rest for documents**
    - Sensitive documents should be encrypted

</details>

