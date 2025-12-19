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

