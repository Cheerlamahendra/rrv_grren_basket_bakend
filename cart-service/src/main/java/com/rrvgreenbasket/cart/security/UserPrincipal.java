
package com.rrvgreenbasket.cart.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final String userId;
    private final String role;
    private final String sessionId;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(String userId, String role, String sessionId) {
        this.userId = userId;
        this.role = role;
        this.sessionId = sessionId;

        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("User role is required");
        }

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }
        if (!normalizedRole.startsWith("ROLE_")) {
            normalizedRole = "ROLE_" + normalizedRole;
        }

        this.authorities = List.of(
                new SimpleGrantedAuthority(normalizedRole)
        );
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isCustomer() {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
