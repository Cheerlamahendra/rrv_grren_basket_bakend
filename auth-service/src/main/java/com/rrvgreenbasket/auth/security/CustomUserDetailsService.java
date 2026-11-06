package com.rrvgreenbasket.auth.security;

import com.rrvgreenbasket.auth.entity.AdminAuth;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.entity.CustomerAuth;
import com.rrvgreenbasket.auth.repository.AdminAuthRepository;
import com.rrvgreenbasket.auth.repository.AuthUserRepository;
import com.rrvgreenbasket.auth.repository.CustomerAuthRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * CustomUserDetailsService
 *
 * <p><strong>What it does:</strong></p>
 * Resolves user principals by email (for administrators), mobile number (for customers),
 * or public UUID.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminAuthRepository adminAuthRepository;
    private final CustomerAuthRepository customerAuthRepository;
    private final AuthUserRepository authUserRepository;

    public CustomUserDetailsService(
            AdminAuthRepository adminAuthRepository,
            CustomerAuthRepository customerAuthRepository,
            AuthUserRepository authUserRepository) {
        this.adminAuthRepository = adminAuthRepository;
        this.customerAuthRepository = customerAuthRepository;
        this.authUserRepository = authUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // 1. Try finding by Admin email
        Optional<AdminAuth> adminAuth = adminAuthRepository.findByEmail(identifier);
        if (adminAuth.isPresent()) {
            AdminAuth admin = adminAuth.get();
            return new CustomUserDetails(admin.getAuthUser(), admin.getEmail(), admin.getPasswordHash());
        }

        // 2. Try finding by Customer mobile number
        Optional<CustomerAuth> customerAuth = customerAuthRepository.findByMobileNumber(identifier);
        if (customerAuth.isPresent()) {
            CustomerAuth customer = customerAuth.get();
            return new CustomUserDetails(customer.getUser(), customer.getMobileNumber(), null);
        }

        // 3. Try finding by publicId
        Optional<AuthUser> authUser = authUserRepository.findByPublicId(identifier);
        if (authUser.isPresent()) {
            return new CustomUserDetails(authUser.get());
        }

        throw new UsernameNotFoundException("User not found with identifier: " + identifier);
    }
}
