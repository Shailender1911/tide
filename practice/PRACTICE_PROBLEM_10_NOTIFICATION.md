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

