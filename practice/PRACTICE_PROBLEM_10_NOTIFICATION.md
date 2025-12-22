# Practice Problem 10: Notification Service API (Medium)

## Context
A junior developer has written a notification service API that handles sending emails, SMS, and push notifications to users. Review this code and identify all issues.

**Time Limit**: 30 minutes  
**Difficulty**: Medium  
**Expected Issues**: 15+

---

## Code to Review

```java
package com.platform.notifications;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Notification controller for sending alerts to users.
 * Supports email, SMS, and push notifications.
 */
@RequestMapping("/notifications")
@RestController
public class NotificationController {

    @Autowired
    public EmailService emailService;

    @Autowired
    public SmsService smsService;

    @Autowired
    public PushService pushService;

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public TemplateRepository templateRepository;

    public static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    // Track sent notifications to prevent duplicates
    private static Set<String> sentNotifications = new HashSet<>();

    @PostMapping("/send")
    public void sendNotification(@RequestParam String userId,
                                 @RequestParam String channel,
                                 @RequestParam String templateId,
                                 @RequestParam String customMessage,
                                 @RequestParam boolean highPriority) {

        User user = userRepository.findById(userId).get();
        Template template = templateRepository.findById(templateId).get();

        String message = template.getContent().replace("{{name}}", user.getName());
        if (customMessage != null) {
            message = message + " " + customMessage;
        }

        String notificationKey = userId + "-" + templateId + "-" + System.currentTimeMillis() / 60000;
        
        if (sentNotifications.contains(notificationKey)) {
            return;
        }
        sentNotifications.add(notificationKey);

        switch (channel) {
            case "EMAIL":
                emailService.send(user.getEmail(), template.getSubject(), message);
                break;
            case "SMS":
                smsService.send(user.getPhone(), message);
                break;
            case "PUSH":
                pushService.send(user.getDeviceToken(), template.getTitle(), message);
                break;
        }

        logger.info("Notification sent to " + user.getEmail() + " phone: " + user.getPhone());
    }

    @PostMapping("/broadcast")
    public void broadcastNotification(@RequestParam String templateId,
                                      @RequestParam String channel,
                                      @RequestParam boolean testMode) {

        Template template = templateRepository.findById(templateId).get();
        List<User> users;

        if (testMode) {
            users = userRepository.findByRole("TESTER");
        } else {
            users = userRepository.findAll();
        }

        for (User user : users) {
            sendNotification(user.getId(), channel, templateId, null, false);
        }
    }

    @GetMapping("/preferences/{userId}")
    public NotificationPreferences getPreferences(@PathVariable String userId) {
        User user = userRepository.findById(userId).get();
        return user.getNotificationPreferences();
    }

    @PutMapping("/preferences/{userId}")
    public void updatePreferences(@PathVariable String userId,
                                 @RequestParam boolean emailEnabled,
                                 @RequestParam boolean smsEnabled,
                                 @RequestParam boolean pushEnabled) {

        User user = userRepository.findById(userId).get();
        NotificationPreferences prefs = user.getNotificationPreferences();
        prefs.setEmailEnabled(emailEnabled);
        prefs.setSmsEnabled(smsEnabled);
        prefs.setPushEnabled(pushEnabled);
        userRepository.save(user);
    }

    @PostMapping("/schedule")
    public void scheduleNotification(@RequestParam String userId,
                                    @RequestParam String channel,
                                    @RequestParam String templateId,
                                    @RequestParam String scheduledTime) {

        // Store for later processing
        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(UUID.randomUUID().toString());
        sn.setUserId(userId);
        sn.setChannel(channel);
        sn.setTemplateId(templateId);
        sn.setScheduledTime(scheduledTime);
        sn.setStatus("PENDING");
        // TODO: Save to database
    }

    @DeleteMapping("/unsubscribe")
    public void unsubscribe(@RequestParam String email,
                           @RequestParam String channel) {

        User user = userRepository.findByEmail(email);
        NotificationPreferences prefs = user.getNotificationPreferences();
        
        if (channel == "EMAIL") {
            prefs.setEmailEnabled(false);
        } else if (channel == "SMS") {
            prefs.setSmsEnabled(false);
        }
        
        userRepository.save(user);
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Focus on: Security, PII logging, Rate limiting, Preferences respect
3. Consider notification service best practices

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- PII (email, phone) in logs
- User preferences not checked before sending
- Static Set not thread-safe
- No authorization on preference endpoints
- testMode from client in broadcast
- == for string comparison in unsubscribe
- Scheduled notification not saved
- No rate limiting for notifications
- Duplicate prevention logic issues
- Broadcast can send to ALL users

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Issues

1. **testMode from client in broadcast** (Lines 68, 73-77)
   ```java
   @RequestParam boolean testMode
   if (testMode) {
       users = userRepository.findByRole("TESTER");
   } else {
       users = userRepository.findAll();
   }
   // Setting testMode=false sends to ALL users!
   // Accidental or malicious broadcast to entire user base
   // Fix: Server-side role check for broadcast, confirmation step
   ```

2. **User preferences NOT checked** (Lines 53-60)
   ```java
   switch (channel) {
       case "EMAIL": emailService.send(...);
   }
   // User might have opted out of emails!
   // Fix: Check user.getNotificationPreferences().isEmailEnabled()
   ```

3. **PII logged** (Line 63)
   ```java
   logger.info("Notification sent to " + user.getEmail() + " phone: " + user.getPhone());
   // Email and phone numbers in logs - PII exposure
   // Fix: Log userId only, or mask email/phone
   ```

4. **No authorization on preferences** (Lines 82-98)
   ```java
   @GetMapping("/preferences/{userId}")
   @PutMapping("/preferences/{userId}")
   // User A can view/modify User B's notification preferences
   // Fix: Verify current user == userId or has admin role
   ```

5. **Static non-thread-safe Set** (Line 30)
   ```java
   private static Set<String> sentNotifications = new HashSet<>();
   // HashSet is NOT thread-safe
   // Concurrent requests can corrupt data
   // Lost on restart - won't prevent duplicates after restart
   // Won't work in distributed system
   // Fix: Use ConcurrentHashMap or database-based deduplication
   ```

6. **== for String comparison** (Lines 121-125)
   ```java
   if (channel == "EMAIL") {
   } else if (channel == "SMS") {
   // Compares references, not values - will always be false!
   // Fix: "EMAIL".equals(channel)
   ```

### 🔴 MAJOR Issues

7. **Scheduled notification not saved** (Lines 100-113)
   ```java
   // TODO: Save to database
   // Notification never actually scheduled!
   // Data lost after request completes
   ```

8. **No response bodies** (All void methods)
   ```java
   public void sendNotification(...)
   // No confirmation, notification ID, or status returned
   ```

9. **Optional.get() without check** (Lines 39-40, 70, 84, 91, 118)
   ```java
   userRepository.findById(userId).get()
   ```

10. **Broadcast without pagination** (Lines 67-80)
    ```java
    users = userRepository.findAll();
    for (User user : users) {
        sendNotification(...);
    }
    // Could be millions of users - memory exhaustion
    // Should use pagination/streaming
    ```

11. **No rate limiting** (Lines 33-63)
    ```java
    @PostMapping("/send")
    // No limit on how many notifications can be sent
    // Could spam users or exhaust SMS credits
    ```

12. **Duplicate key logic flawed** (Line 46)
    ```java
    String notificationKey = userId + "-" + templateId + "-" + System.currentTimeMillis() / 60000;
    // Only prevents duplicates within same minute
    // Same notification can be sent again next minute
    ```

### 🟡 MEDIUM Issues

13. **Field injection** (Lines 14-26)
    ```java
    @Autowired public EmailService
    ```

14. **No validation on channel** (Lines 53-60)
    ```java
    switch (channel) {
        case "EMAIL": ...
        // No default case - invalid channel silently ignored
    ```

15. **String for scheduledTime** (Lines 103, 109)
    ```java
    @RequestParam String scheduledTime
    sn.setScheduledTime(scheduledTime);
    // No validation of date format
    // Should use LocalDateTime with parsing
    ```

16. **Unsubscribe uses DELETE with body** (Lines 115-127)
    ```java
    @DeleteMapping("/unsubscribe")
    @RequestParam String email
    // DELETE with request params is unconventional
    // Should be POST or use path params
    ```

17. **findByEmail returns single user** (Line 118)
    ```java
    User user = userRepository.findByEmail(email);
    // Could be null - no check
    // Multiple users with same email? (data issue)
    ```

18. **HighPriority parameter unused** (Line 37)
    ```java
    @RequestParam boolean highPriority
    // Parameter accepted but never used in logic
    ```

### 🟢 MINOR Issues

19. **Preferences returned directly** (Lines 82-85)
    ```java
    return user.getNotificationPreferences();
    // Should return DTO, might expose internal fields
    ```

20. **No logging of failed notifications**
    - Should log failures for debugging

21. **Broadcast call to sendNotification inefficient** (Line 79)
    ```java
    sendNotification(user.getId(), channel, templateId, null, false);
    // Re-fetches user and template for each iteration
    // Should batch process
    ```

22. **No unsubscribe token validation** (Lines 115-127)
    ```java
    @RequestParam String email
    // Anyone who knows an email can unsubscribe them
    // Should require authentication or signed token
    ```

</details>

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Notification Controller

```java
package com.platform.notifications;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private static final Set<String> VALID_CHANNELS = Set.of("EMAIL", "SMS", "PUSH");

    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final UserRepository userRepository;
    private final TemplateRepository templateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ScheduledNotificationRepository scheduledNotificationRepository;

    // Constructor injection...

    @PostMapping("/send")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<NotificationResponse> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        // FIX: Authorization - can only send to self or with ADMIN role
        if (!Objects.equals(request.getUserId(), currentUserId) && 
            !SecurityContext.hasRole("ADMIN")) {
            throw new UnauthorizedException("Cannot send notifications to other users");
        }

        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new UserNotFoundException(request.getUserId()));

        // FIX: Check user preferences before sending
        NotificationPreferences prefs = user.getNotificationPreferences();
        if (!isChannelEnabled(request.getChannel(), prefs)) {
            logger.info("Notification skipped - user opted out of channel: {}", request.getChannel());
            return ResponseEntity.ok(new NotificationResponse(null, "SKIPPED_USER_PREFERENCE"));
        }

        // FIX: Validate channel
        if (!VALID_CHANNELS.contains(request.getChannel())) {
            throw new InvalidChannelException("Invalid channel: " + request.getChannel());
        }

        Template template = templateRepository.findById(request.getTemplateId())
            .orElseThrow(() -> new TemplateNotFoundException(request.getTemplateId()));

        String message = template.getContent().replace("{{name}}", user.getName());
        if (request.getCustomMessage() != null) {
            message = message + " " + request.getCustomMessage();
        }

        // FIX: Database-based deduplication
        String deduplicationKey = generateDeduplicationKey(request);
        if (notificationLogRepository.existsByDeduplicationKey(deduplicationKey)) {
            logger.info("Duplicate notification prevented - key: {}", deduplicationKey);
            return ResponseEntity.ok(new NotificationResponse(null, "DUPLICATE"));
        }

        String notificationId = UUID.randomUUID().toString();

        // Send based on channel
        switch (request.getChannel()) {
            case "EMAIL" -> emailService.send(user.getEmail(), template.getSubject(), message);
            case "SMS" -> smsService.send(user.getPhone(), message);
            case "PUSH" -> pushService.send(user.getDeviceToken(), template.getTitle(), message);
            default -> throw new InvalidChannelException("Unknown channel");
        }

        // Log the notification (for deduplication and audit)
        NotificationLog log = new NotificationLog();
        log.setId(notificationId);
        log.setUserId(request.getUserId());
        log.setChannel(request.getChannel());
        log.setTemplateId(request.getTemplateId());
        log.setDeduplicationKey(deduplicationKey);
        log.setSentAt(Instant.now());
        log.setStatus("SENT");
        notificationLogRepository.save(log);

        // FIX: Log WITHOUT PII
        logger.info("Notification sent - id: {}, userId: {}, channel: {}", 
                   notificationId, request.getUserId(), request.getChannel());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new NotificationResponse(notificationId, "SENT"));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")  // FIX: Server-side admin check
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<BroadcastResponse> broadcastNotification(
            @Valid @RequestBody BroadcastRequest request) {

        String adminUserId = SecurityContext.getCurrentUserId();

        Template template = templateRepository.findById(request.getTemplateId())
            .orElseThrow(() -> new TemplateNotFoundException(request.getTemplateId()));

        // FIX: Stream/paginate to avoid memory issues
        int totalSent = 0;
        int page = 0;
        int pageSize = 100;

        while (true) {
            Page<User> usersPage = userRepository.findAll(Pageable.ofSize(pageSize).withPage(page));
            
            for (User user : usersPage.getContent()) {
                try {
                    sendToUser(user, request.getChannel(), template);
                    totalSent++;
                } catch (Exception e) {
                    logger.error("Broadcast failed for user: {}", user.getId(), e);
                }
            }

            if (!usersPage.hasNext()) break;
            page++;
        }

        logger.info("Broadcast completed - admin: {}, template: {}, sent: {}", 
                   adminUserId, request.getTemplateId(), totalSent);

        return ResponseEntity.ok(new BroadcastResponse(totalSent, "COMPLETED"));
    }

    @GetMapping("/preferences")
    public ResponseEntity<PreferencesResponse> getPreferences() {
        // FIX: Only return current user's preferences
        String currentUserId = SecurityContext.getCurrentUserId();

        User user = userRepository.findById(currentUserId)
            .orElseThrow(() -> new UserNotFoundException(currentUserId));

        NotificationPreferences prefs = user.getNotificationPreferences();
        return ResponseEntity.ok(new PreferencesResponse(
            prefs.isEmailEnabled(),
            prefs.isSmsEnabled(),
            prefs.isPushEnabled()
        ));
    }

    @PutMapping("/preferences")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<PreferencesResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request) {

        // FIX: Only update current user's preferences
        String currentUserId = SecurityContext.getCurrentUserId();

        User user = userRepository.findById(currentUserId)
            .orElseThrow(() -> new UserNotFoundException(currentUserId));

        NotificationPreferences prefs = user.getNotificationPreferences();
        prefs.setEmailEnabled(request.isEmailEnabled());
        prefs.setSmsEnabled(request.isSmsEnabled());
        prefs.setPushEnabled(request.isPushEnabled());
        userRepository.save(user);

        logger.info("Preferences updated - userId: {}", currentUserId);

        return ResponseEntity.ok(new PreferencesResponse(
            request.isEmailEnabled(),
            request.isSmsEnabled(),
            request.isPushEnabled()
        ));
    }

    @PostMapping("/schedule")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<ScheduleResponse> scheduleNotification(
            @Valid @RequestBody ScheduleRequest request) {

        String currentUserId = SecurityContext.getCurrentUserId();

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(UUID.randomUUID().toString());
        sn.setUserId(request.getUserId());
        sn.setChannel(request.getChannel());
        sn.setTemplateId(request.getTemplateId());
        sn.setScheduledTime(request.getScheduledTime());  // Instant type
        sn.setStatus("PENDING");
        sn.setCreatedBy(currentUserId);
        sn.setCreatedAt(Instant.now());

        // FIX: Actually save to database!
        scheduledNotificationRepository.save(sn);

        logger.info("Notification scheduled - id: {}, for: {}", sn.getId(), request.getScheduledTime());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ScheduleResponse(sn.getId(), request.getScheduledTime()));
    }

    @PostMapping("/unsubscribe")  // FIX: POST with token, not DELETE with email
    public ResponseEntity<Void> unsubscribe(
            @RequestParam String token) {  // FIX: Signed token, not raw email

        // Validate and decode the unsubscribe token
        UnsubscribeToken decoded = tokenService.validateUnsubscribeToken(token);
        if (decoded == null) {
            throw new InvalidTokenException("Invalid or expired unsubscribe token");
        }

        User user = userRepository.findById(decoded.getUserId())
            .orElseThrow(() -> new UserNotFoundException(decoded.getUserId()));

        NotificationPreferences prefs = user.getNotificationPreferences();

        // FIX: Use .equals() for string comparison
        if ("EMAIL".equals(decoded.getChannel())) {
            prefs.setEmailEnabled(false);
        } else if ("SMS".equals(decoded.getChannel())) {
            prefs.setSmsEnabled(false);
        }

        userRepository.save(user);

        logger.info("User unsubscribed - userId: {}, channel: {}", 
                   decoded.getUserId(), decoded.getChannel());

        return ResponseEntity.ok().build();
    }

    private boolean isChannelEnabled(String channel, NotificationPreferences prefs) {
        return switch (channel) {
            case "EMAIL" -> prefs.isEmailEnabled();
            case "SMS" -> prefs.isSmsEnabled();
            case "PUSH" -> prefs.isPushEnabled();
            default -> false;
        };
    }

    private String generateDeduplicationKey(SendNotificationRequest request) {
        return request.getUserId() + "-" + request.getTemplateId() + "-" + 
               Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).toEpochMilli();
    }
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| `testMode` from client | Client can broadcast | `@PreAuthorize("hasRole('ADMIN')")` |
| User preferences | Not checked | Check before sending |
| PII in logs | Email, phone logged | Only userId logged |
| Deduplication | Static HashSet | Database-based |
| String comparison | `==` | `.equals()` |
| Scheduled notification | Not saved | Save to database |
| Unsubscribe | Raw email | Signed token |
| Broadcast memory | findAll() | Paginated streaming |

</details>

