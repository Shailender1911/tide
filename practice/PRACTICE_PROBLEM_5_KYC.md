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

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed KYC Controller

```java
package com.bank.kyc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * KYC verification controller for compliance.
 * Handles identity document verification with proper security and audit trails.
 */
@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private static final Logger logger = LoggerFactory.getLogger(KycController.class);
    private static final Set<String> VALID_DOCUMENT_TYPES = 
        Set.of("PASSPORT", "DRIVING_LICENSE", "NATIONAL_ID");
    private static final Set<String> VALID_KYC_STATUSES = 
        Set.of("PENDING", "VERIFIED", "REJECTED", "UNDER_REVIEW");

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final StorageService storageService;
    private final AuditService auditService;

    public KycController(DocumentRepository documentRepository,
                        UserRepository userRepository,
                        VerificationService verificationService,
                        StorageService storageService,
                        AuditService auditService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.verificationService = verificationService;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    /**
     * Submit identity document for KYC verification.
     */
    @PostMapping("/verify/{userId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<KycResponse> verifyIdentity(
            @PathVariable String userId,
            @Valid @RequestBody KycVerificationRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();
        
        // FIX: Authorization - user can only submit for themselves
        if (!Objects.equals(userId, currentUserId)) {
            logger.warn("Unauthorized KYC attempt - currentUser: {}, targetUser: {}", 
                       currentUserId, userId);
            throw new UnauthorizedException("Cannot submit KYC for another user");
        }

        // FIX: Validate document type
        if (!VALID_DOCUMENT_TYPES.contains(request.getDocumentType())) {
            throw new InvalidDocumentTypeException(
                "Invalid document type. Allowed: " + VALID_DOCUMENT_TYPES);
        }

        logger.info("KYC verification initiated - userId: {}, docType: {}", 
                   userId, request.getDocumentType());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // FIX: Store document in secure storage (not in DB as base64)
        String documentPath = storageService.storeSecurely(
            request.getDocumentImage(),
            userId,
            request.getDocumentType()
        );

        // Create document record (no document number in logs!)
        Document doc = new Document();
        doc.setId(UUID.randomUUID().toString());
        doc.setUserId(userId);
        doc.setType(request.getDocumentType());
        doc.setNumberHash(hashDocumentNumber(request.getDocumentNumber()));  // FIX: Hash, don't store plain
        doc.setStoragePath(documentPath);  // Reference to secure storage
        doc.setCreatedAt(Instant.now());
        doc.setStatus("PENDING");
        documentRepository.save(doc);

        // Verify with external service
        boolean isValid = verificationService.verifyDocument(
            request.getDocumentType(), 
            request.getDocumentNumber()
        );

        String newStatus = isValid ? "VERIFIED" : "REJECTED";
        String previousStatus = user.getKycStatus();

        user.setKycStatus(newStatus);
        if (isValid) {
            user.setKycVerifiedAt(Instant.now());
        }
        userRepository.save(user);

        // FIX: Detailed audit logging (no PII!)
        auditService.logKycAction(AuditEntry.builder()
            .action("KYC_VERIFICATION")
            .userId(userId)
            .documentId(doc.getId())
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .timestamp(Instant.now())
            .build());

        // FIX: Log without PII
        logger.info("KYC completed - userId: {}, status: {}, documentId: {}", 
                   userId, newStatus, doc.getId());

        return ResponseEntity.ok(new KycResponse(
            doc.getId(),
            newStatus,
            isValid ? user.getKycVerifiedAt() : null
        ));
    }

    /**
     * Get KYC status (authenticated users only).
     */
    @GetMapping("/status/{userId}")  // FIX: Path param, not query param
    public ResponseEntity<KycStatusResponse> getKycStatus(@PathVariable String userId) {

        String currentUserId = SecurityContext.getCurrentUserId();
        
        // FIX: Authorization check
        if (!Objects.equals(userId, currentUserId) && !SecurityContext.hasRole("COMPLIANCE")) {
            throw new UnauthorizedException("Not authorized to view this KYC status");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // FIX: Return DTO, not PII
        return ResponseEntity.ok(new KycStatusResponse(
            user.getKycStatus(),
            user.getKycVerifiedAt()
            // NO document number in response!
        ));
    }

    /**
     * Manual KYC override (compliance officers only).
     */
    @PutMapping("/override/{userId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")  // FIX: Server-side role check
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<KycResponse> manualOverride(
            @PathVariable String userId,
            @Valid @RequestBody KycOverrideRequest request) {

        String complianceOfficerId = SecurityContext.getCurrentUserId();
        
        // FIX: Validate status value
        if (!VALID_KYC_STATUSES.contains(request.getNewStatus())) {
            throw new InvalidKycStatusException(
                "Invalid status. Allowed: " + VALID_KYC_STATUSES);
        }

        logger.info("KYC override initiated - userId: {}, newStatus: {}, officer: {}", 
                   userId, request.getNewStatus(), complianceOfficerId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        String previousStatus = user.getKycStatus();
        user.setKycStatus(request.getNewStatus());
        user.setKycOverrideReason(request.getReason());
        user.setKycOverrideBy(complianceOfficerId);
        
        if ("VERIFIED".equals(request.getNewStatus())) {
            user.setKycVerifiedAt(Instant.now());
        }
        
        userRepository.save(user);

        // FIX: Critical audit log for compliance override
        auditService.logKycAction(AuditEntry.builder()
            .action("KYC_MANUAL_OVERRIDE")
            .userId(userId)
            .performedBy(complianceOfficerId)
            .previousStatus(previousStatus)
            .newStatus(request.getNewStatus())
            .reason(request.getReason())
            .timestamp(Instant.now())
            .build());

        logger.info("KYC override completed - userId: {}, by: {}, from: {} to: {}", 
                   userId, complianceOfficerId, previousStatus, request.getNewStatus());

        return ResponseEntity.ok(new KycResponse(
            null,
            request.getNewStatus(),
            user.getKycVerifiedAt()
        ));
    }

    /**
     * Delete user documents (GDPR compliance - with audit).
     */
    @DeleteMapping("/documents/{userId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> deleteUserDocuments(@PathVariable String userId) {

        String currentUserId = SecurityContext.getCurrentUserId();
        
        // Authorization - user can delete own docs, or admin
        if (!Objects.equals(userId, currentUserId) && !SecurityContext.hasRole("ADMIN")) {
            throw new UnauthorizedException("Not authorized to delete these documents");
        }

        logger.info("Document deletion requested - userId: {}, requestedBy: {}", 
                   userId, currentUserId);

        List<Document> docs = documentRepository.findByUserId(userId);
        
        for (Document doc : docs) {
            // Soft delete with audit trail (GDPR requirement)
            doc.setStatus("DELETED");
            doc.setDeletedAt(Instant.now());
            doc.setDeletedBy(currentUserId);
            documentRepository.save(doc);

            // Delete from secure storage
            storageService.delete(doc.getStoragePath());
        }

        // FIX: Audit trail for GDPR compliance
        auditService.logDeletion(AuditEntry.builder()
            .action("GDPR_DOCUMENT_DELETION")
            .userId(userId)
            .requestedBy(currentUserId)
            .documentCount(docs.size())
            .timestamp(Instant.now())
            .build());

        logger.info("Documents deleted - userId: {}, count: {}", userId, docs.size());

        return ResponseEntity.noContent().build();
    }

    private String hashDocumentNumber(String documentNumber) {
        // Use secure hashing (SHA-256 or bcrypt)
        return EncryptionUtils.hash(documentNumber);
    }
}
```

### DTOs

```java
public class KycVerificationRequest {
    @NotBlank
    @Pattern(regexp = "PASSPORT|DRIVING_LICENSE|NATIONAL_ID")
    private String documentType;

    @NotBlank
    private String documentNumber;

    @NotBlank
    private String documentImage;  // Base64 or pre-signed URL
}

public class KycOverrideRequest {
    @NotBlank
    private String newStatus;

    @NotBlank
    @Size(min = 10, max = 500)
    private String reason;  // Required for compliance
}

public record KycResponse(
    String documentId,
    String status,
    Instant verifiedAt
) {}

public record KycStatusResponse(
    String status,
    Instant verifiedAt
    // NO document number!
) {}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| `skipVerification` | Client parameter | REMOVED |
| `isCompliance` | Client parameter | `@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")` |
| Document number in logs | Plain text | NEVER logged |
| Document number in response | Exposed | Hash stored, not returned |
| Document image storage | Base64 in DB | Secure storage service |
| Status validation | None | Validate against allowed set |
| Audit logging | None | Comprehensive audit trail |
| GDPR deletion | Hard delete, no audit | Soft delete with audit |
| Authorization | Missing | Check ownership or role |

</details>

