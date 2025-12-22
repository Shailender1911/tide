# Practice Problem 9: Document Upload API (Medium)

## Context
A junior developer has written a document upload API for a banking application. Users can upload identity documents, statements, and other files. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.bank.documents;

import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document upload controller.
 * Supports uploading identity documents and bank statements.
 */
@RequestMapping("/documents")
@RestController
public class DocumentController {

    @Autowired
    public DocumentRepository documentRepository;

    @Autowired
    public UserRepository userRepository;

    public static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private static final String UPLOAD_DIR = "/var/uploads/";

    @PostMapping("/upload")
    public String uploadDocument(@RequestParam String userId,
                                 @RequestParam String documentType,
                                 @RequestParam MultipartFile file) throws IOException {

        User user = userRepository.findById(userId).get();

        // Save file to disk
        String filename = file.getOriginalFilename();
        String filepath = UPLOAD_DIR + filename;
        
        File dest = new File(filepath);
        file.transferTo(dest);

        // Save record
        Document doc = new Document();
        doc.setId(UUID.randomUUID().toString());
        doc.setUserId(userId);
        doc.setType(documentType);
        doc.setFilename(filename);
        doc.setFilepath(filepath);
        doc.setSize(file.getSize());
        doc.setUploadedAt(new Date());
        documentRepository.save(doc);

        logger.info("File uploaded: " + filepath + " for user: " + userId);

        return doc.getId();
    }

    @GetMapping("/download/{documentId}")
    public byte[] downloadDocument(@PathVariable String documentId) throws IOException {
        Document doc = documentRepository.findById(documentId).get();
        
        File file = new File(doc.getFilepath());
        FileInputStream fis = new FileInputStream(file);
        byte[] data = fis.readAllBytes();
        fis.close();
        
        return data;
    }

    @GetMapping("/list")
    public List<Document> listDocuments(@RequestParam String userId) {
        return documentRepository.findByUserId(userId);
    }

    @DeleteMapping("/{documentId}")
    public void deleteDocument(@PathVariable String documentId) {
        Document doc = documentRepository.findById(documentId).get();
        
        File file = new File(doc.getFilepath());
        file.delete();
        
        documentRepository.delete(doc);
    }

    @PostMapping("/share")
    public void shareDocument(@RequestParam String documentId,
                             @RequestParam String recipientEmail,
                             @RequestParam boolean publicAccess) {

        Document doc = documentRepository.findById(documentId).get();
        
        if (publicAccess) {
            doc.setPublicUrl("/public/docs/" + doc.getFilename());
        }
        
        doc.setSharedWith(recipientEmail);
        documentRepository.save(doc);
    }

    @GetMapping("/public/docs/{filename}")
    public byte[] getPublicDocument(@PathVariable String filename) throws IOException {
        File file = new File(UPLOAD_DIR + filename);
        FileInputStream fis = new FileInputStream(file);
        return fis.readAllBytes();
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: File security, Path traversal, Authorization, Resource handling
3. Consider file upload security best practices

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Path traversal attack (../../../etc/passwd)
- No file type validation (could upload .exe, .jsp)
- No authorization - anyone can view/delete anyone's documents
- File size limits
- Resource leaks (FileInputStream not in try-with-resources)
- Public access without owner consent
- Original filename used (could overwrite files)
- Sensitive document paths logged
- No content-type headers on download

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Security Issues

1. **Path Traversal Vulnerability** (Lines 33-34, 95-97)
   ```java
   String filename = file.getOriginalFilename();
   String filepath = UPLOAD_DIR + filename;
   // Attacker can upload with filename: "../../../etc/passwd"
   // Or overwrite other files: "../../app/config.properties"
   
   // In getPublicDocument:
   File file = new File(UPLOAD_DIR + filename);
   // Attacker can access: /public/docs/../../../etc/passwd
   
   // Fix: Sanitize filename, use UUID for storage, validate path
   ```

2. **No File Type Validation** (Lines 26-28)
   ```java
   @RequestParam MultipartFile file
   // No validation of file type
   // Could upload: malware.exe, shell.jsp, virus.pdf
   // Fix: Validate content-type AND file magic bytes
   ```

3. **No Authorization on Any Endpoint**
   ```java
   @GetMapping("/download/{documentId}")
   @GetMapping("/list")
   @DeleteMapping("/{documentId}")
   @PostMapping("/share")
   // User A can download/delete User B's documents
   // Fix: Verify current user owns the document
   ```

4. **publicAccess without owner consent** (Lines 83-84, 86-88)
   ```java
   @RequestParam boolean publicAccess
   if (publicAccess) {
       doc.setPublicUrl("/public/docs/" + doc.getFilename());
   }
   // Anyone can make anyone's documents public!
   // Critical privacy/security violation
   ```

5. **Public endpoint exposes all files** (Lines 93-98)
   ```java
   @GetMapping("/public/docs/{filename}")
   public byte[] getPublicDocument(@PathVariable String filename)
   // No validation if file should be public
   // Any file in UPLOAD_DIR accessible by guessing filename
   ```

6. **Original filename overwrite attack** (Lines 33-34)
   ```java
   String filename = file.getOriginalFilename();
   // Two users upload "passport.pdf" - one overwrites the other
   // Fix: Use UUID or userId prefix for unique storage
   ```

### 🔴 MAJOR Issues

7. **Resource leak - FileInputStream not closed** (Lines 58-61, 95-97)
   ```java
   FileInputStream fis = new FileInputStream(file);
   byte[] data = fis.readAllBytes();
   fis.close();  // Won't close if exception thrown!
   
   // Fix: Use try-with-resources
   try (FileInputStream fis = new FileInputStream(file)) {
       return fis.readAllBytes();
   }
   ```

8. **No file size limit** (Lines 26-28)
   ```java
   @RequestParam MultipartFile file
   // User could upload 10GB file, crash server
   // Fix: @RequestParam(required=true) + configure max upload size
   // spring.servlet.multipart.max-file-size=10MB
   ```

9. **No response headers on download** (Lines 55-64)
   ```java
   public byte[] downloadDocument(...)
   // No Content-Type header - browser won't know file type
   // No Content-Disposition - won't trigger download
   // Fix: Return ResponseEntity with proper headers
   ```

10. **Optional.get() without check** (Lines 31, 57, 72, 82)
    ```java
    documentRepository.findById(documentId).get()
    ```

11. **Delete doesn't verify ownership** (Lines 70-77)
    ```java
    @DeleteMapping("/{documentId}")
    public void deleteDocument(@PathVariable String documentId)
    // User A can delete User B's documents
    ```

12. **File deletion can fail silently** (Lines 74-75)
    ```java
    file.delete();  // Returns boolean, not checked
    documentRepository.delete(doc);
    // DB record deleted even if file deletion failed
    ```

### 🟡 MEDIUM Issues

13. **Field injection** (Lines 16-20)
    ```java
    @Autowired public DocumentRepository
    ```

14. **Sensitive path logged** (Line 49)
    ```java
    logger.info("File uploaded: " + filepath + " for user: " + userId);
    // Full server path exposed in logs
    ```

15. **IOException propagated to client** (Lines 28, 55, 94)
    ```java
    throws IOException
    // Stack trace might be exposed to client
    // Fix: Handle exception, return proper error response
    ```

16. **java.util.Date** (Line 46)
    ```java
    doc.setUploadedAt(new Date());
    ```

17. **No pagination on list** (Lines 66-68)
    ```java
    return documentRepository.findByUserId(userId);
    // Could return thousands of documents
    ```

18. **Document entities returned directly** (Line 67)
    ```java
    public List<Document> listDocuments(...)
    // Might expose internal fields, file paths
    // Use DTOs
    ```

### 🟢 MINOR Issues

19. **Hardcoded upload directory** (Line 23)
    ```java
    private static final String UPLOAD_DIR = "/var/uploads/";
    // Should be configurable property
    ```

20. **No virus scanning**
    - Uploaded files should be scanned for malware

21. **No encryption at rest**
    - Sensitive documents stored unencrypted

22. **void return for delete** (Line 71)
    - No confirmation returned

23. **Share endpoint sets sharedWith but doesn't send notification**
    - Recipient never notified

</details>

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Document Controller

```java
package com.bank.documents;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/png"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;  // 10MB

    @Value("${document.upload.dir}")
    private String uploadDir;

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;  // FIX: Use secure storage service
    private final VirusScanService virusScanService;

    // Constructor injection...

    @PostMapping("/upload")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam String documentType) {

        String currentUserId = SecurityContext.getCurrentUserId();

        // FIX: Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileTooLargeException("File size exceeds 10MB limit");
        }

        // FIX: Validate file type (check content, not just extension)
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException("File type not allowed: " + contentType);
        }

        // FIX: Virus scan
        if (virusScanService.isInfected(file)) {
            logger.warn("Infected file upload attempt - user: {}", currentUserId);
            throw new MalwareDetectedException("File failed security scan");
        }

        // FIX: Generate unique filename (prevent overwrites)
        String uniqueFilename = UUID.randomUUID() + "_" + 
            sanitizeFilename(file.getOriginalFilename());

        // FIX: Validate path doesn't escape upload directory (path traversal)
        Path targetPath = Paths.get(uploadDir, currentUserId, uniqueFilename).normalize();
        if (!targetPath.startsWith(Paths.get(uploadDir).normalize())) {
            logger.warn("Path traversal attempt - user: {}", currentUserId);
            throw new SecurityException("Invalid file path");
        }

        try {
            // Create user directory if needed
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file", e);
        }

        Document doc = new Document();
        doc.setId(UUID.randomUUID().toString());
        doc.setUserId(currentUserId);
        doc.setType(documentType);
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setStoredFilename(uniqueFilename);
        doc.setContentType(contentType);
        doc.setSize(file.getSize());
        doc.setUploadedAt(Instant.now());
        documentRepository.save(doc);

        // FIX: Log without exposing full path
        logger.info("Document uploaded - id: {}, type: {}, user: {}", 
                   doc.getId(), documentType, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(doc));
    }

    @GetMapping("/download/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String documentId) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        // FIX: Authorization check
        if (!Objects.equals(doc.getUserId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized to download this document");
        }

        Path filePath = Paths.get(uploadDir, doc.getUserId(), doc.getStoredFilename());

        // FIX: Use try-with-resources
        byte[] data;
        try {
            data = Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read file", e);
        }

        // FIX: Proper response headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(doc.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(doc.getOriginalFilename())
            .build());
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @GetMapping("/list")
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String currentUserId = SecurityContext.getCurrentUserId();

        // FIX: Only return current user's documents
        Page<Document> docs = documentRepository.findByUserId(
            currentUserId, PageRequest.of(page, size)
        );

        return ResponseEntity.ok(docs.map(this::toResponse));
    }

    @DeleteMapping("/{documentId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> deleteDocument(@PathVariable String documentId) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        // FIX: Authorization
        if (!Objects.equals(doc.getUserId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized");
        }

        Path filePath = Paths.get(uploadDir, doc.getUserId(), doc.getStoredFilename());

        try {
            // FIX: Check deletion result
            boolean deleted = Files.deleteIfExists(filePath);
            if (!deleted) {
                logger.warn("File not found during deletion: {}", documentId);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete file", e);
        }

        documentRepository.delete(doc);

        logger.info("Document deleted - id: {}, user: {}", documentId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    // FIX: Remove public access endpoint - use signed URLs instead
    // No @GetMapping("/public/docs/{filename}")

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        // Remove path separators and dangerous characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
            doc.getId(),
            doc.getType(),
            doc.getOriginalFilename(),
            doc.getSize(),
            doc.getUploadedAt()
            // NO file path in response!
        );
    }
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| Path traversal | `UPLOAD_DIR + filename` | Normalize and validate path |
| File type validation | None | Check content-type + magic bytes |
| File overwrites | Original filename | UUID prefix |
| Authorization | None | Check doc.userId == currentUser |
| Public access | Anyone can access | Removed (use signed URLs) |
| Resource leak | Manual close | try-with-resources |
| Download headers | None | Content-Type, Content-Disposition |
| Virus scanning | None | Scan before storing |
| Path in logs | Full server path | Only document ID |

</details>

