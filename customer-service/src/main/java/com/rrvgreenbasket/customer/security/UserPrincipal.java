package com.rrvgreenbasket.customer.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * UserPrincipal
 *
 * <p><strong>What it does:</strong></p>
 * Represents the authenticated caller's identity within Customer Service.
 * Holds the external canonical public UUID (<code>userId</code>), email/identifier,
 * roles, active session ID, and account status.
 */
@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final String userId; // Canonical AuthUser publicId (UUID)
    private final String email;
    private final String role;
    private final String sessionId;
    private final String accountStatus;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(String userId, String email, String role, String sessionId, String accountStatus) {
        String effectiveRole = (role != null && !role.isBlank()) ? role : "ROLE_CUSTOMER";
        if (!effectiveRole.startsWith("ROLE_")) {
            effectiveRole = "ROLE_" + effectiveRole;
        }

        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(effectiveRole)
        );

        return UserPrincipal.builder()
                .userId(userId)
                .email(email)
                .role(effectiveRole)
                .sessionId(sessionId)
                .accountStatus(accountStatus != null ? accountStatus : "ACTIVE")
                .authorities(authorities)
                .build();
    }

    public static UserPrincipal create(String userId, String email, String role) {
        return create(userId, email, role, null, "ACTIVE");
    }

    public boolean isCustomer() {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
    }

    public boolean isAdmin() {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
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
        return !"BLOCKED".equalsIgnoreCase(accountStatus);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equalsIgnoreCase(accountStatus);
    }
}
