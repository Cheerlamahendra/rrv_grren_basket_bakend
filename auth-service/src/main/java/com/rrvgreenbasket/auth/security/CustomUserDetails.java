package com.rrvgreenbasket.auth.security;

import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * CustomUserDetails
 *
 * <p><strong>What it does:</strong></p>
 * Implements Spring Security {@link UserDetails} backed by {@link AuthUser}.
 */
public class CustomUserDetails implements UserDetails {

    private final AuthUser authUser;
    private final Long id;
    private final String publicId;
    private final String username;
    private final String password;
    private final UserRole role;
    private final UserStatus status;

    public CustomUserDetails(AuthUser authUser) {
        this.authUser = authUser;
        this.id = authUser.getId();
        this.publicId = authUser.getPublicId();
        this.role = authUser.getRole();
        this.status = authUser.getStatus();

        if (authUser.getAdminAuth() != null) {
            this.password = authUser.getAdminAuth().getPasswordHash();
            this.username = authUser.getAdminAuth().getEmail();
        } else if (authUser.getCustomerAuth() != null) {
            this.password = null;
            this.username = authUser.getCustomerAuth().getMobileNumber();
        } else {
            this.password = null;
            this.username = authUser.getPublicId();
        }
    }

    public CustomUserDetails(AuthUser authUser, String username, String password) {
        this.authUser = authUser;
        this.id = authUser.getId();
        this.publicId = authUser.getPublicId();
        this.role = authUser.getRole();
        this.status = authUser.getStatus();
        this.username = username;
        this.password = password;
    }

    public AuthUser getAuthUser() {
        return authUser;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BLOCKED && status != UserStatus.DISABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
