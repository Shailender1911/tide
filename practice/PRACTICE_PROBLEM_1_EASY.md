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

---

## ✅ Fixed Code Solution

<details>
<summary>Click to reveal the corrected implementation</summary>

### Fixed Controller

```java
package com.app.users;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User registration and authentication controller.
 */
@RestController
@RequestMapping("/api/v1/users")  // FIX: Added API versioning
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    // FIX: Constructor injection with private final fields
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Register a new user.
     */
    @PostMapping("/register")  // FIX: POST for creating resources
    @Transactional  // FIX: Ensure atomicity of save + email
    public ResponseEntity<UserResponse> registerUser(
            @Valid @RequestBody RegistrationRequest request) {  // FIX: Request body with validation

        logger.info("Registration attempt for email: {}", request.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            logger.warn("Registration failed - email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException(request.getEmail());  // FIX: Proper exception (409)
        }

        // Create user
        User user = new User();
        user.setId(UUID.randomUUID().toString());  // FIX: UUID instead of Random
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));  // FIX: Encrypt password!
        user.setName(request.getName());
        user.setCreatedAt(Instant.now());  // FIX: Use Instant

        userRepository.save(user);

        // Send welcome email
        emailService.sendEmail(request.getEmail(), "Welcome!", "Thanks for registering");

        // FIX: Use logger, NEVER log passwords
        logger.info("User registered successfully: userId={}", user.getId());

        // FIX: Return response DTO (not entity)
        UserResponse response = new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate a user.
     */
    @PostMapping("/login")  // FIX: POST for login (sends credentials)
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        logger.info("Login attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail());
        
        // FIX: Null check before accessing user
        if (user == null) {
            logger.warn("Login failed - user not found: {}", request.getEmail());
            throw new UnauthorizedException("Invalid credentials");  // FIX: 401, not 500
        }

        // FIX: Use passwordEncoder.matches() for secure comparison
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Login failed - invalid password for: {}", request.getEmail());
            throw new UnauthorizedException("Invalid credentials");
        }

        logger.info("Login successful for userId: {}", user.getId());

        // FIX: Return DTO without sensitive data
        LoginResponse response = new LoginResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            generateToken(user)  // JWT or session token
        );

        return ResponseEntity.ok(response);
    }

    private String generateToken(User user) {
        // JWT generation logic
        return "jwt-token-here";
    }
}
```

### Request/Response DTOs

```java
// Registration Request with validation
public class RegistrationRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100)
    private String name;

    // Getters and setters
}

// Login Request
public class LoginRequest {
    
    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    // Getters and setters
}

// User Response (no password!)
public record UserResponse(
    String id,
    String email,
    String name,
    Instant createdAt
) {}

// Login Response
public record LoginResponse(
    String id,
    String email,
    String name,
    String token
) {}
```

### Custom Exceptions

```java
@ResponseStatus(HttpStatus.CONFLICT)  // 409
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email);
    }
}

@ResponseStatus(HttpStatus.UNAUTHORIZED)  // 401
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
```

### Key Fixes Summary

| Issue | Original | Fixed |
|-------|----------|-------|
| Password storage | Plain text | `passwordEncoder.encode()` |
| Password comparison | `==` | `passwordEncoder.matches()` |
| Password in logs | `System.out.println(password)` | Never log passwords |
| HTTP method | `@GetMapping` | `@PostMapping` |
| Response | `void` | `ResponseEntity<UserResponse>` |
| ID generation | `Random().nextInt(10000)` | `UUID.randomUUID()` |
| User returned | Entity with password | DTO without password |
| Null check | Missing | Check before access |
| Error codes | 500 for everything | 401, 409 appropriately |
| Dependency injection | Field injection, public | Constructor, private final |

</details>

