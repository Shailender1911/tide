# Practice Problem 7: Invoice Generation API (Medium)

## Context
A junior developer has written an invoice generation API for a business banking platform. Businesses can create and send invoices to their customers. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.bank.invoicing;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Invoice controller for business customers.
 * Supports creating, sending, and managing invoices.
 */
@RequestMapping("/invoices")
@RestController
public class InvoiceController {

    @Autowired
    public InvoiceRepository invoiceRepository;

    @Autowired
    public BusinessRepository businessRepository;

    @Autowired
    public EmailService emailService;

    @Autowired
    public PdfGenerator pdfGenerator;

    public static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    private static int invoiceCounter = 1000;

    @PostMapping("/create")
    public String createInvoice(@RequestParam String businessId,
                                @RequestParam String customerEmail,
                                @RequestParam String customerName,
                                @RequestParam double amount,
                                @RequestParam double taxRate,
                                @RequestParam String description,
                                @RequestParam String dueDate) {

        Business business = businessRepository.findById(businessId).get();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-" + invoiceCounter++);
        invoice.setBusinessId(businessId);
        invoice.setCustomerEmail(customerEmail);
        invoice.setCustomerName(customerName);
        invoice.setAmount(amount);
        invoice.setTaxAmount(amount * taxRate);
        invoice.setTotalAmount(amount + (amount * taxRate));
        invoice.setDescription(description);
        invoice.setDueDate(dueDate);
        invoice.setStatus("DRAFT");
        invoice.setCreatedAt(new Date());

        invoiceRepository.save(invoice);

        return invoice.getInvoiceNumber();
    }

    @PutMapping("/send/{invoiceId}")
    public void sendInvoice(@PathVariable String invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        Business business = businessRepository.findById(invoice.getBusinessId()).get();

        // Generate PDF
        byte[] pdf = pdfGenerator.generate(invoice);

        // Send email
        emailService.sendWithAttachment(
            invoice.getCustomerEmail(),
            "Invoice from " + business.getName(),
            "Please find attached invoice for " + invoice.getTotalAmount(),
            pdf
        );

        invoice.setStatus("SENT");
        invoice.setSentAt(new Date());
        invoiceRepository.save(invoice);
    }

    @GetMapping("/list")
    public List<Invoice> getInvoices(@RequestParam String odBusinessId) {
        return invoiceRepository.findByBusinessId(odBusinessId);
    }

    @PostMapping("/mark-paid/{invoiceId}")
    public void markAsPaid(@PathVariable String invoiceId,
                          @RequestParam double paidAmount,
                          @RequestParam boolean overrideMismatch) {

        Invoice invoice = invoiceRepository.findById(invoiceId).get();

        if (paidAmount == invoice.getTotalAmount() || overrideMismatch) {
            invoice.setStatus("PAID");
            invoice.setPaidAmount(paidAmount);
            invoice.setPaidAt(new Date());
            invoiceRepository.save(invoice);
        } else {
            throw new InternalServerError();
        }
    }

    @DeleteMapping("/{invoiceId}")
    public void deleteInvoice(@PathVariable String invoiceId) {
        invoiceRepository.deleteById(invoiceId);
    }

    @GetMapping("/download/{invoiceId}")
    public byte[] downloadInvoice(@PathVariable String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).get();
        return pdfGenerator.generate(invoice);
    }

    @PostMapping("/duplicate/{invoiceId}")
    public String duplicateInvoice(@PathVariable String invoiceId) {
        Invoice original = invoiceRepository.findById(invoiceId).get();

        Invoice copy = new Invoice();
        copy.setInvoiceNumber("INV-" + invoiceCounter++);
        copy.setBusinessId(original.getBusinessId());
        copy.setCustomerEmail(original.getCustomerEmail());
        copy.setCustomerName(original.getCustomerName());
        copy.setAmount(original.getAmount());
        copy.setTaxAmount(original.getTaxAmount());
        copy.setTotalAmount(original.getTotalAmount());
        copy.setDescription(original.getDescription());
        copy.setStatus("DRAFT");
        copy.setCreatedAt(new Date());

        invoiceRepository.save(copy);
        return copy.getInvoiceNumber();
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Authorization, Data integrity, Concurrency, Money handling
3. Consider business requirements for invoicing

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- Static counter for invoice numbers (thread safety, persistence)
- Authorization - can anyone access anyone's invoices?
- overrideMismatch parameter from client
- double for money amounts
- Tax calculations precision
- Response types (void, raw bytes)
- Delete without soft-delete (audit trail)
- No transaction for send operation
- String date format
- == comparison for amounts

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Issues

1. **No authorization checks anywhere**
   ```java
   @GetMapping("/list")
   public List<Invoice> getInvoices(@RequestParam String odBusinessId)
   // Anyone can view any business's invoices!
   // All endpoints lack ownership verification
   // Fix: Verify current user owns/has access to the business
   ```

2. **overrideMismatch from client** (Lines 81, 85)
   ```java
   @RequestParam boolean overrideMismatch
   if (paidAmount == invoice.getTotalAmount() || overrideMismatch)
   // Anyone can mark any invoice as paid with any amount
   // Financial fraud potential!
   // Fix: Server-side role check for override capability
   ```

3. **Static invoice counter** (Line 27, 41, 111)
   ```java
   private static int invoiceCounter = 1000;
   invoice.setInvoiceNumber("INV-" + invoiceCounter++);
   // Not thread-safe - concurrent requests get same number
   // Resets on server restart - duplicate invoice numbers!
   // Won't work in distributed/clustered environment
   // Fix: Database sequence or UUID
   ```

4. **double for money** (Lines 33-34, 44-46, 82, 85)
   ```java
   double amount, double taxRate, double paidAmount
   invoice.setTaxAmount(amount * taxRate);
   // Precision errors in financial calculations
   // Fix: Use BigDecimal with proper rounding
   ```

### 🔴 MAJOR Issues

5. **Tax calculation precision** (Lines 45-46)
   ```java
   invoice.setTaxAmount(amount * taxRate);
   invoice.setTotalAmount(amount + (amount * taxRate));
   // Double precision issues + should round to 2 decimal places
   // Different results possible from: amount * (1 + taxRate)
   ```

6. **== comparison for amounts** (Line 85)
   ```java
   if (paidAmount == invoice.getTotalAmount() || ...)
   // Double comparison with == is unreliable
   // Fix: Use BigDecimal.compareTo() or tolerance comparison
   ```

7. **No response body for most operations** (Lines 55, 79, 93)
   ```java
   public void sendInvoice(...)
   public void markAsPaid(...)
   public void deleteInvoice(...)
   // No confirmation, timestamp, or status returned
   ```

8. **Optional.get() without check** (Lines 38, 57-58, 83, 101, 106)
   ```java
   invoiceRepository.findById(invoiceId).get()
   // NPE if invoice not found
   ```

9. **Hard delete without audit** (Lines 93-95)
   ```java
   invoiceRepository.deleteById(invoiceId);
   // Invoices are legal documents - should soft delete
   // Need audit trail for compliance
   ```

10. **No transaction for send** (Lines 55-74)
    ```java
    byte[] pdf = pdfGenerator.generate(invoice);
    emailService.sendWithAttachment(...);
    invoice.setStatus("SENT");
    invoiceRepository.save(invoice);
    // If save fails after email sent, status not updated
    // If email fails after PDF generated, no cleanup
    ```

11. **Date as String** (Lines 36, 48)
    ```java
    @RequestParam String dueDate
    invoice.setDueDate(dueDate);
    // No validation of date format
    // Should use LocalDate with proper parsing
    ```

### 🟡 MEDIUM Issues

12. **Field injection** (Lines 14-24)
    ```java
    @Autowired public InvoiceRepository
    ```

13. **InternalServerError for payment mismatch** (Line 91)
    ```java
    throw new InternalServerError();
    // Should be 400 Bad Request with message
    ```

14. **Raw bytes returned** (Lines 97-100)
    ```java
    public byte[] downloadInvoice(...)
    // No content-type header, no content-disposition
    // Should return ResponseEntity with proper headers
    ```

15. **Logger not used**
    - No audit logging for invoice operations
    - Should log: create, send, pay, delete

16. **java.util.Date** (Lines 50, 72, 89, 119)
    ```java
    new Date()
    ```

17. **Invoice list returns entities** (Lines 76-78)
    ```java
    public List<Invoice> getInvoices(...)
    // Should return DTOs, might expose internal fields
    ```

### 🟢 MINOR Issues

18. **Typo in parameter** (Line 77)
    ```java
    @RequestParam String odBusinessId  // "od" typo
    ```

19. **No pagination for list** (Lines 76-78)
    ```java
    return invoiceRepository.findByBusinessId(odBusinessId);
    // Could return thousands of invoices
    // Should have pagination
    ```

20. **No validation on inputs**
    - Email format not validated
    - Amount could be negative
    - Tax rate could be negative or > 100%
    - Description could be empty

21. **Duplicate doesn't copy dueDate** (Lines 105-120)
    - Missing field in duplicate operation

22. **No email validation before send** (Line 57)
    - Should validate email format before attempting send

</details>

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Invoice Controller

```java
package com.bank.invoicing;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceRepository invoiceRepository;
    private final BusinessRepository businessRepository;
    private final EmailService emailService;
    private final PdfGenerator pdfGenerator;
    private final InvoiceNumberService invoiceNumberService;  // FIX: DB-based invoice numbers

    // Constructor injection...

    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody InvoiceCreateRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Business business = businessRepository.findById(request.getBusinessId())
            .orElseThrow(() -> new BusinessNotFoundException(request.getBusinessId()));

        // FIX: Authorization - verify business ownership
        if (!Objects.equals(business.getOwnerId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized to create invoices for this business");
        }

        // FIX: Database-generated invoice number (thread-safe)
        String invoiceNumber = invoiceNumberService.generateNextNumber(request.getBusinessId());

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID().toString());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setBusinessId(request.getBusinessId());
        invoice.setCustomerEmail(request.getCustomerEmail());
        invoice.setCustomerName(request.getCustomerName());

        // FIX: BigDecimal for money with proper rounding
        BigDecimal amount = request.getAmount();
        BigDecimal taxAmount = amount.multiply(request.getTaxRate())
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = amount.add(taxAmount);

        invoice.setAmount(amount);
        invoice.setTaxRate(request.getTaxRate());
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setDescription(request.getDescription());
        invoice.setDueDate(request.getDueDate());  // FIX: LocalDate type
        invoice.setStatus("DRAFT");
        invoice.setCreatedAt(Instant.now());

        invoiceRepository.save(invoice);

        logger.info("Invoice created - number: {}, business: {}, total: {}", 
                   invoiceNumber, request.getBusinessId(), totalAmount);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(invoice));
    }

    @PutMapping("/send/{invoiceId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<InvoiceResponse> sendInvoice(@PathVariable String invoiceId) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        // FIX: Authorization
        Business business = businessRepository.findById(invoice.getBusinessId())
            .orElseThrow(() -> new BusinessNotFoundException(invoice.getBusinessId()));

        if (!Objects.equals(business.getOwnerId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized");
        }

        byte[] pdf = pdfGenerator.generate(invoice);

        emailService.sendWithAttachment(
            invoice.getCustomerEmail(),
            "Invoice from " + business.getName(),
            "Please find attached invoice for " + invoice.getTotalAmount(),
            pdf
        );

        invoice.setStatus("SENT");
        invoice.setSentAt(Instant.now());
        invoiceRepository.save(invoice);

        logger.info("Invoice sent - id: {}, to: {}", invoiceId, invoice.getCustomerEmail());

        return ResponseEntity.ok(toResponse(invoice));
    }

    @PostMapping("/mark-paid/{invoiceId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<InvoiceResponse> markAsPaid(
            @PathVariable String invoiceId,
            @Valid @RequestBody MarkPaidRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        // FIX: Authorization
        Business business = businessRepository.findById(invoice.getBusinessId())
            .orElseThrow(() -> new BusinessNotFoundException(invoice.getBusinessId()));

        if (!Objects.equals(business.getOwnerId(), currentUserId)) {
            throw new UnauthorizedException("Not authorized");
        }

        // FIX: Use BigDecimal.compareTo()
        if (request.getPaidAmount().compareTo(invoice.getTotalAmount()) != 0) {
            // Require exact match or throw clear error
            throw new PaymentMismatchException(
                String.format("Payment amount %s does not match invoice total %s", 
                             request.getPaidAmount(), invoice.getTotalAmount()));
        }

        invoice.setStatus("PAID");
        invoice.setPaidAmount(request.getPaidAmount());
        invoice.setPaidAt(Instant.now());
        invoiceRepository.save(invoice);

        logger.info("Invoice marked paid - id: {}, amount: {}", invoiceId, request.getPaidAmount());

        return ResponseEntity.ok(toResponse(invoice));
    }

    @DeleteMapping("/{invoiceId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> deleteInvoice(@PathVariable String invoiceId) {

        String currentUserId = SecurityContext.getCurrentUserId();

        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        // Authorization check...

        // FIX: Soft delete for legal compliance
        invoice.setStatus("DELETED");
        invoice.setDeletedAt(Instant.now());
        invoice.setDeletedBy(currentUserId);
        invoiceRepository.save(invoice);

        logger.info("Invoice soft-deleted - id: {}, by: {}", invoiceId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download/{invoiceId}")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable String invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        // Authorization check...

        byte[] pdf = pdfGenerator.generate(invoice);

        // FIX: Proper response headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("invoice-" + invoice.getInvoiceNumber() + ".pdf")
            .build());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/list")
    public ResponseEntity<Page<InvoiceResponse>> getInvoices(
            @RequestParam String businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {  // FIX: Pagination

        String currentUserId = SecurityContext.getCurrentUserId();

        // Authorization check...

        Page<Invoice> invoices = invoiceRepository.findByBusinessId(
            businessId, PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        return ResponseEntity.ok(invoices.map(this::toResponse));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getDueDate(),
            invoice.getCreatedAt()
        );
    }
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| Invoice number | Static counter | Database sequence service |
| `overrideMismatch` | Client parameter | Removed - require exact match |
| Money type | `double` | `BigDecimal` with rounding |
| Authorization | None | Check business ownership |
| Delete | Hard delete | Soft delete with audit |
| Download response | Raw bytes | ResponseEntity with headers |
| List | All at once | Paginated |
| Date | String | LocalDate |

</details>

