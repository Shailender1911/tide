# Practice Problem 1: User Registration API (Easy)

## Context
A junior developer has written a user registration endpoint. Review this code and identify all issues.

**Time Limit**: 20 minutes  
**Difficulty**: Easy  
**Expected Issues**: 10-12

---

## Code to Review

```java
package com.app.users;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User registration controller
 */
@RequestMapping("/api/users")
@RestController
public class UserController {

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public PasswordEncoder passwordEncoder;

    @Autowired
    public EmailService emailService;

    public static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/register")
    public void registerUser(@RequestParam String email,
                             @RequestParam String password,
                             @RequestParam String name) {

        // Check if user already exists
        User existingUser = userRepository.findByEmail(email);
        if (existingUser == null) {
            
            User user = new User();
            user.setId(new Random().nextInt(10000));
            user.setEmail(email);
            user.setPassword(password);
            user.setName(name);
            user.setCreatedAt(new Date());

            userRepository.save(user);

            // Send welcome email
            emailService.sendEmail(email, "Welcome!", "Thanks for registering");

            System.out.println("User registered: " + email + " with password: " + password);
        } else {
            throw new RuntimeException("User exists");
        }
    }

    @PostMapping("/login")
    public User login(@RequestParam String email,
                      @RequestParam String password) {

        User user = userRepository.findByEmail(email);
        if (user.getPassword() == password) {
            return user;
        }
        throw new InternalServerError();
    }
}
```

---

## Your Task

1. Identify all bugs and issues
2. Categorize them by severity (CRITICAL, MAJOR, MINOR)
3. Suggest fixes for each issue
4. Prioritize the most important issues

---

## Hints (Don't look until you've tried!)

<details>
<summary>Click to reveal hints</summary>

Think about:
- HTTP methods for different operations
- Password handling and security
- Response types
- Error handling
- Logging practices
- Input validation
- ID generation
- String comparison
- Dependency injection patterns

</details>

---

## Solution

<details>
<summary>Click to reveal solution</summary>

### 🔴 CRITICAL Issues

1. **Password stored in plain text** (Line 35)
   ```java
   user.setPassword(password);  // Should be encrypted!
   // Fix: user.setPassword(passwordEncoder.encode(password));
   ```

2. **Password logged in plain text** (Line 43)
   ```java
   System.out.println("... with password: " + password);  // Security breach!
   // Fix: Never log passwords. Remove this line.
   ```

3. **Password comparison with ==** (Line 53)
   ```java
   if (user.getPassword() == password)  // Compares references!
   // Fix: if (passwordEncoder.matches(password, user.getPassword()))
   ```

4. **User entity returned directly** (Line 54)
   ```java
   return user;  // Exposes password hash and internal data!
   // Fix: Return a DTO without sensitive fields
   ```

### 🔴 MAJOR Issues

5. **GET for registration** (Line 24)
   ```java
   @GetMapping("/register")  // Should be POST for creating resources
   // Fix: @PostMapping("/register")
   ```

6. **No response body for registration** (Line 25)
   ```java
   public void registerUser  // Client gets no confirmation
   // Fix: Return ResponseEntity<UserResponse>
   ```

7. **Random ID generation** (Line 33)
   ```java
   user.setId(new Random().nextInt(10000));  // Collision risk!
   // Fix: Use UUID or database auto-generation
   ```

8. **Wrong status for user exists** (Line 46)
   ```java
   throw new RuntimeException("User exists");  // Should be 409 Conflict
   // Fix: throw new UserAlreadyExistsException(email);
   ```

9. **Null pointer risk** (Line 52)
   ```java
   if (user.getPassword() == password)  // user could be null!
   // Fix: Check if user is null first
   ```

10. **InternalServerError for auth failure** (Line 56)
    ```java
    throw new InternalServerError();  // Should be 401 Unauthorized
    ```

### 🟡 MEDIUM Issues

11. **Field injection with public fields** (Lines 13-20)
    ```java
    @Autowired public UserRepository  // Should be private + constructor injection
    ```

12. **No input validation** (Lines 25-27)
    ```java
    // No validation on email format, password strength, name length
    // Fix: Use @Valid with Bean Validation
    ```

13. **Using System.out instead of logger** (Line 43)
    ```java
    System.out.println(...)  // Use logger instead
    ```

14. **Logger declared but not used properly** (Line 22)
    ```java
    // Logger exists but System.out.println is used
    ```

15. **No transaction management**
    - Email could be sent but database save could fail

### 🟢 MINOR Issues

16. **Using java.util.Date** (Line 37)
    ```java
    new Date()  // Use Instant or LocalDateTime
    ```

17. **Query params for sensitive data** (Lines 49-50)
    ```java
    @RequestParam String password  // Should be in request body
    ```

</details>

