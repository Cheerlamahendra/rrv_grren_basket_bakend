package com.rrvgreenbasket.order.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails, Principal {

    private final String userId;
    private final String role;
    private final String sessionId;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(String userId, String role, String sessionId) {
        this.userId = userId;
        this.role = role != null ? role.toUpperCase() : "CUSTOMER";
        this.sessionId = sessionId;

        String formattedRole = this.role.startsWith("ROLE_") ? this.role : "ROLE_" + this.role;
        this.authorities = List.of(new SimpleGrantedAuthority(formattedRole));
    }

    @Override
    public String getName() {
        return userId;
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
