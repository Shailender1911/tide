package vnd.credit.loans.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for accessing authentication context.
 * 
 * <p>This provides a centralized way to access the current authenticated
 * user's information from Spring Security context.</p>
 * 
 * <h3>Why Server-Side Auth Context?</h3>
 * <p>The original code had a critical security vulnerability:</p>
 * <pre>{@code
 * // BAD: isAdminAgent comes from client - anyone can bypass auth!
 * @RequestParam boolean isAdminAgent
 * if (!isAdminAgent) {
 *     // check ownership
 * }
 * }</pre>
 * 
 * <p>Instead, authorization should always be checked server-side:</p>
 * <pre>{@code
 * // GOOD: Get user from server-side security context
 * String currentUserId = AuthContext.getCurrentUserID();
 * 
 * // GOOD: Check roles server-side
 * boolean isAdmin = AuthContext.hasRole("ADMIN");
 * }</pre>
 * 
 * @author Tide Engineering Team
 * @since v3
 */
public final class AuthContext {

    private AuthContext() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the current authenticated user's ID.
     * 
     * <p>This retrieves the user ID from Spring Security's
     * SecurityContextHolder, which is populated by the authentication
     * filter chain.</p>
     *
     * @return the current user's ID
     * @throws IllegalStateException if no user is authenticated
     */
    public static String getCurrentUserID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        
        // Assuming the principal is the user ID or username
        // In real applications, this might be a custom UserDetails object
        return authentication.getName();
    }

    /**
     * Checks if the current user has a specific role.
     * 
     * <p>This is the correct way to check for admin privileges,
     * NOT from a client-provided request parameter.</p>
     *
     * @param role the role to check (without ROLE_ prefix)
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            return false;
        }
        
        String roleWithPrefix = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(roleWithPrefix));
    }

    /**
     * Checks if the current user is an admin.
     *
     * @return true if user has ADMIN role, false otherwise
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Gets the current authentication object.
     *
     * @return the Authentication object, or null if not authenticated
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}

