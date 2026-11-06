package com.rrvgreenbasket.inventory.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "SYSTEM";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return (userPrincipal.getEmail() != null && !userPrincipal.getEmail().isBlank())
                    ? userPrincipal.getEmail()
                    : userPrincipal.getUserId();
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
