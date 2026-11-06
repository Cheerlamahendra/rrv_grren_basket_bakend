package com.rrvgreenbasket.auth.config;

import com.rrvgreenbasket.auth.entity.AdminAuth;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import com.rrvgreenbasket.auth.repository.AdminAuthRepository;
import com.rrvgreenbasket.auth.repository.AuthUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DataInitializer
 *
 * <p><strong>What it does:</strong></p>
 * Ensures initial Super Admin and dedicated Test Admin credentials exist
 * in <code>auth_users</code> and <code>admin_auth</code> on startup.
 * The primary development admin is kept distinct from the test admin to prevent
 * accidental lockout during automated negative testing.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AuthUserRepository authUserRepository;
    private final AdminAuthRepository adminAuthRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.test-admin.email:testadmin@rrvgreenbasket.com}")
    private String testAdminEmail;

    @Value("${app.security.test-admin.password:TestAdmin@123}")
    private String testAdminPassword;

    public DataInitializer(
            AuthUserRepository authUserRepository,
            AdminAuthRepository adminAuthRepository,
            PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.adminAuthRepository = adminAuthRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin("admin@rrvgreenbasket.com", "Admin@123", "Primary Super Admin");
        seedAdmin(testAdminEmail, testAdminPassword, "Dedicated Testing Admin");
    }

    private void seedAdmin(String email, String rawPassword, String description) {
        String normalizedEmail = email.trim().toLowerCase();
        if (!adminAuthRepository.existsByEmail(normalizedEmail)) {
            LocalDateTime now = LocalDateTime.now();

            AuthUser adminUser = AuthUser.builder()
                    .publicId(UUID.randomUUID().toString())
                    .role(UserRole.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            AdminAuth adminAuth = AdminAuth.builder()
                    .authUser(adminUser)
                    .email(normalizedEmail)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .failedLoginAttempts(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            adminUser.setAdminAuth(adminAuth);
            authUserRepository.save(adminUser);

            log.info("Initialized {} account: {}", description, normalizedEmail);
        }
    }
}
