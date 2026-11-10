package com.rrvgreenbasket.store.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private String userId;
    private String email;
    private String role;
    private String sessionId;
    private String status;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(String userId, String email, String role, String sessionId, String status) {
        String normalizedRole = (role != null) ? role.trim() : "CUSTOMER";
        String authorityName = normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityName));

        return UserPrincipal.builder()
                .userId(userId)
                .email(email)
                .role(normalizedRole)
                .sessionId(sessionId)
                .status(status != null ? status : "ACTIVE")
                .authorities(authorities)
                .build();
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
        return email != null ? email : userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equalsIgnoreCase(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !"DEACTIVATED".equalsIgnoreCase(status) && !"SUSPENDED".equalsIgnoreCase(status);
    }
}
