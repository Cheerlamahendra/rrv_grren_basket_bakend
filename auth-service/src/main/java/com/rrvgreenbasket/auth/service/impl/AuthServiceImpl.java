package com.rrvgreenbasket.auth.service.impl;

import com.rrvgreenbasket.auth.client.CustomerServiceClient;
import com.rrvgreenbasket.auth.constants.AuthConstants;
import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.CustomerLogoutResponse;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.dto.response.UserSummaryResponse;
import com.rrvgreenbasket.auth.entity.*;
import com.rrvgreenbasket.auth.enums.OtpPurpose;
import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import com.rrvgreenbasket.auth.exception.AccountLockedException;
import com.rrvgreenbasket.auth.exception.DuplicateResourceException;
import com.rrvgreenbasket.auth.exception.InvalidOtpException;
import com.rrvgreenbasket.auth.exception.OtpExpiredException;
import com.rrvgreenbasket.auth.exception.ResourceNotFoundException;
import com.rrvgreenbasket.auth.exception.ServiceUnavailableException;
import com.rrvgreenbasket.auth.exception.TokenValidationException;
import com.rrvgreenbasket.auth.mapper.UserMapper;
import com.rrvgreenbasket.auth.repository.AdminAuthRepository;
import com.rrvgreenbasket.auth.repository.AuthUserRepository;
import com.rrvgreenbasket.auth.repository.CustomerAuthRepository;
import com.rrvgreenbasket.auth.repository.RefreshTokenRepository;
import com.rrvgreenbasket.auth.service.AuthAuditService;
import com.rrvgreenbasket.auth.service.AuthService;
import com.rrvgreenbasket.auth.service.JwtTokenService;
import com.rrvgreenbasket.auth.service.OtpService;
import com.rrvgreenbasket.auth.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * AuthServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Central business service implementation orchestrating Customer passwordless auth,
 * multi-device session tracking, two-phase Registration Saga with profile synchronization,
 * Admin credentials auth, and token rotation.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthUserRepository authUserRepository;
    private final CustomerAuthRepository customerAuthRepository;
    private final AdminAuthRepository adminAuthRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final JwtTokenService jwtTokenService;
    private final SessionService sessionService;
    private final CustomerServiceClient customerServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthAuditService auditService;

    private final int adminMaxFailedAttempts;
    private final int adminLockoutMinutes;
    private final long accessExpirationMs;

    public AuthServiceImpl(
            AuthUserRepository authUserRepository,
            CustomerAuthRepository customerAuthRepository,
            AdminAuthRepository adminAuthRepository,
            RefreshTokenRepository refreshTokenRepository,
            OtpService otpService,
            JwtTokenService jwtTokenService,
            SessionService sessionService,
            CustomerServiceClient customerServiceClient,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuthAuditService auditService,
            @Value("${app.security.admin-max-failed-attempts:5}") int adminMaxFailedAttempts,
            @Value("${app.security.admin-lockout-duration-minutes:15}") int adminLockoutMinutes,
            @Value("${app.jwt.access-token-expiration-ms:1800000}") long accessExpirationMs) {
        this.authUserRepository = authUserRepository;
        this.customerAuthRepository = customerAuthRepository;
        this.adminAuthRepository = adminAuthRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpService = otpService;
        this.jwtTokenService = jwtTokenService;
        this.sessionService = sessionService;
        this.customerServiceClient = customerServiceClient;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.adminMaxFailedAttempts = adminMaxFailedAttempts;
        this.adminLockoutMinutes = adminLockoutMinutes;
        this.accessExpirationMs = accessExpirationMs;
    }

    private String normalizeMobile(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replaceAll("[^0-9+]", "").trim();
        if (cleaned.startsWith("+91")) {
            return cleaned;
        }
        if (cleaned.length() == 10) {
            return "+91" + cleaned;
        }
        if (!cleaned.startsWith("+")) {
            return "+" + cleaned;
        }
        return cleaned;
    }

    // ==============================================================================
    // Dedicated Customer Registration & Login Workflows
    // ==============================================================================

    @Override
    @Transactional
    public OtpResponse sendCustomerRegistrationOtp(CustomerRegisterSendOtpRequest request, String clientIp) {
        String normalizedMobile = normalizeMobile(request.getMobileNumber());

        Optional<CustomerAuth> existingAuthOpt = customerAuthRepository.findByMobileNumber(normalizedMobile);
        if (existingAuthOpt.isPresent() && existingAuthOpt.get().getUser().getStatus() == UserStatus.ACTIVE) {
            auditService.logEvent(existingAuthOpt.get().getUser(), "REGISTRATION_OTP_REJECTED", false, clientIp, "Customer already registered: " + normalizedMobile);
            throw new DuplicateResourceException("Account with mobile number " + normalizedMobile + " already exists. Please login instead.");
        }

        return otpService.generateAndSendOtp(normalizedMobile, OtpPurpose.REGISTRATION, clientIp, request.getFullName(), request.getEmail());
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class, OtpExpiredException.class, ServiceUnavailableException.class})
    public AuthResponse verifyCustomerRegistrationOtp(CustomerRegisterVerifyOtpRequest request, String clientIp, String userAgent) {
        String normalizedMobile = normalizeMobile(request.getMobileNumber());

        // 1. Fetch challenge to preserve staged full_name and email
        OtpChallenge challenge = otpService.getActiveChallenge(normalizedMobile, OtpPurpose.REGISTRATION)
                .orElseThrow(() -> new InvalidOtpException("No active registration OTP challenge found for " + normalizedMobile + ". Please request a new OTP."));

        String fullName = (challenge.getFullName() != null && !challenge.getFullName().isBlank()) ? challenge.getFullName() : request.getFullName();
        String email = (challenge.getEmail() != null && !challenge.getEmail().isBlank()) ? challenge.getEmail() : request.getEmail();

        if (fullName == null || fullName.isBlank()) {
            fullName = "Customer " + (normalizedMobile.length() >= 4 ? normalizedMobile.substring(normalizedMobile.length() - 4) : "User");
        }

        // 2. Uniqueness check against existing ACTIVE accounts
        Optional<CustomerAuth> existingAuthOpt = customerAuthRepository.findByMobileNumber(normalizedMobile);
        if (existingAuthOpt.isPresent() && existingAuthOpt.get().getUser().getStatus() == UserStatus.ACTIVE) {
            throw new DuplicateResourceException("Account with mobile number " + normalizedMobile + " already exists. Please login instead.");
        }

        // 3. Verify OTP single-use challenge
        otpService.verifyOtp(normalizedMobile, request.getOtp(), OtpPurpose.REGISTRATION, clientIp);

        // 4. Two-Phase Registration Saga: Phase 1 (Stage Auth in PENDING status or reuse existing PENDING)
        LocalDateTime now = LocalDateTime.now();
        AuthUser authUser;
        CustomerAuth customerAuth;

        if (existingAuthOpt.isPresent() && existingAuthOpt.get().getUser().getStatus() == UserStatus.PENDING) {
            customerAuth = existingAuthOpt.get();
            authUser = customerAuth.getUser();
            customerAuth.setMobileVerified(true);
            customerAuth.setMobileVerifiedAt(now);
            customerAuth.setUpdatedAt(now);
            authUser.setUpdatedAt(now);
            log.info("Reusing existing PENDING AuthUser publicId='{}' for registration retry", authUser.getPublicId());
        } else {
            String canonicalUserId = UUID.randomUUID().toString();
            authUser = AuthUser.builder()
                    .publicId(canonicalUserId)
                    .role(UserRole.CUSTOMER)
                    .status(UserStatus.PENDING) // Cannot log in until profile provisioned
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            customerAuth = CustomerAuth.builder()
                    .user(authUser)
                    .mobileNumber(normalizedMobile)
                    .mobileVerified(true)
                    .mobileVerifiedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            authUser.setCustomerAuth(customerAuth);
            authUser = authUserRepository.save(authUser);
            log.info("Staged AuthUser in PENDING status with publicId='{}'", canonicalUserId);
        }

        String canonicalUserId = authUser.getPublicId();

        // 5. Two-Phase Registration Saga: Phase 2 (Provision Profile in Customer Service)
        boolean profileProvisioned = false;
        try {
            profileProvisioned = customerServiceClient.initializeCustomerProfile(canonicalUserId, fullName, normalizedMobile, email);
        } catch (Exception ex) {
            log.warn("Customer Service call failed for authUserId='{}': {}", canonicalUserId, ex.getMessage());
        }

        // 6. Handle Outcome with State Verification Probe
        if (!profileProvisioned) {
            boolean existsOnProbe = false;
            try {
                existsOnProbe = customerServiceClient.verifyProfileExists(canonicalUserId);
            } catch (Exception probeEx) {
                log.warn("Probe encountered connectivity issue for authUserId='{}': {}", canonicalUserId, probeEx.getMessage());
            }

            if (existsOnProbe) {
                profileProvisioned = true;
                log.info("Profile existence confirmed via state verification probe for authUserId='{}'", canonicalUserId);
            }
        }

        if (!profileProvisioned) {
            // Preserve recoverable PENDING state without issuing JWT tokens or losing data
            log.warn("Customer Service unavailable during profile creation for authUserId='{}'. Preserving PENDING account.", canonicalUserId);
            auditService.logEvent(authUser, "CUSTOMER_REGISTRATION_PENDING", false, clientIp,
                    "Customer profile provisioning pending for " + normalizedMobile);
            throw new ServiceUnavailableException("Customer service is temporarily unavailable. Your registration details have been saved and will activate shortly. Please try logging in soon.");
        }

        // 7. Success: Promote AuthUser to ACTIVE
        authUser.setStatus(UserStatus.ACTIVE);
        authUser.setUpdatedAt(LocalDateTime.now());
        AuthUser activeUser = authUserRepository.save(authUser);

        // 8. Create independent device session
        CustomerSession session = sessionService.createSession(activeUser, userAgent, clientIp);

        // 9. Generate tokens bound to session
        String accessToken = jwtTokenService.generateAccessToken(activeUser, session.getSessionId());
        String refreshToken = jwtTokenService.createRefreshToken(activeUser, session.getSessionId());

        auditService.logEvent(activeUser, "CUSTOMER_REGISTRATION_SUCCESS", true, clientIp,
                "Customer registered and authenticated: " + normalizedMobile + ", session=" + session.getSessionId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds(accessExpirationMs / 1000)
                .user(userMapper.toSummaryResponse(activeUser))
                .build();
    }

    @Override
    @Transactional
    public OtpResponse sendCustomerLoginOtp(CustomerLoginSendOtpRequest request, String clientIp) {
        String normalizedMobile = normalizeMobile(request.getMobileNumber());

        CustomerAuth customerAuth = customerAuthRepository.findByMobileNumber(normalizedMobile)
                .orElseThrow(() -> {
                    auditService.logEvent(null, "LOGIN_OTP_REJECTED", false, clientIp, "Customer not registered: " + normalizedMobile);
                    return new ResourceNotFoundException("No customer account registered with mobile number: " + normalizedMobile + ". Please register first.");
                });

        if (!customerAuth.isMobileVerified()) {
            auditService.logEvent(customerAuth.getUser(), "LOGIN_OTP_REJECTED", false, clientIp, "Mobile not verified: " + normalizedMobile);
            throw new BadCredentialsException("Mobile number is not verified. Please complete registration first.");
        }

        AuthUser user = customerAuth.getUser();
        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.DISABLED) {
            auditService.logEvent(user, "LOGIN_OTP_BLOCKED", false, clientIp, "Account blocked or disabled");
            throw new BadCredentialsException("Account is blocked or disabled. Please contact customer support.");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            boolean exists = false;
            try {
                exists = customerServiceClient.verifyProfileExists(user.getPublicId());
            } catch (Exception e) {
                log.warn("Probe failed for pending user during login check: {}", e.getMessage());
            }
            if (exists) {
                user.setStatus(UserStatus.ACTIVE);
                authUserRepository.save(user);
                log.info("Promoted PENDING user to ACTIVE during login check: '{}'", user.getPublicId());
            } else {
                auditService.logEvent(user, "LOGIN_OTP_REJECTED", false, clientIp, "Customer registration pending: " + normalizedMobile);
                throw new BadCredentialsException("Customer registration is pending profile activation. Please try again shortly.");
            }
        } else if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.logEvent(user, "LOGIN_OTP_REJECTED", false, clientIp, "Account not active: " + user.getStatus());
            throw new BadCredentialsException("Customer account is not active. Status: " + user.getStatus());
        }

        return otpService.generateAndSendOtp(normalizedMobile, OtpPurpose.LOGIN, clientIp);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class, OtpExpiredException.class})
    public AuthResponse verifyCustomerLoginOtp(CustomerLoginVerifyOtpRequest request, String clientIp, String userAgent) {
        String normalizedMobile = normalizeMobile(request.getMobileNumber());

        // 1. Verify OTP single-use challenge
        otpService.verifyOtp(normalizedMobile, request.getOtp(), OtpPurpose.LOGIN, clientIp);

        // 2. Lookup registered customer
        CustomerAuth customerAuth = customerAuthRepository.findByMobileNumber(normalizedMobile)
                .orElseThrow(() -> new ResourceNotFoundException("No customer account registered with mobile number: " + normalizedMobile));

        if (!customerAuth.isMobileVerified()) {
            throw new BadCredentialsException("Mobile number is not verified. Please complete registration first.");
        }

        AuthUser user = customerAuth.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.logEvent(user, "CUSTOMER_LOGIN_FAILED", false, clientIp, "Account not active: " + user.getStatus());
            throw new BadCredentialsException("Account is not active. Status: " + user.getStatus());
        }

        // 3. Ensure the associated customer profile exists in Customer Service
        boolean profileExists = false;
        try {
            profileExists = customerServiceClient.verifyProfileExists(user.getPublicId());
        } catch (Exception ex) {
            log.warn("Could not probe Customer Service for profile existence for user '{}': {}", user.getPublicId(), ex.getMessage());
            profileExists = true; // S2S network blip should not lock an active customer out
        }

        if (!profileExists) {
            log.warn("Customer profile missing in Customer Service for authUserId='{}'", user.getPublicId());
            throw new ResourceNotFoundException("Customer profile not found in Customer Service. Please contact support.");
        }

        // 4. Create fresh independent device session
        CustomerSession session = sessionService.createSession(user, userAgent, clientIp);

        // 5. Issue tokens bound to this session
        String accessToken = jwtTokenService.generateAccessToken(user, session.getSessionId());
        String refreshToken = jwtTokenService.createRefreshToken(user, session.getSessionId());

        auditService.logEvent(user, "CUSTOMER_LOGIN_SUCCESS", true, clientIp,
                "Customer authenticated via OTP: " + normalizedMobile + ", session=" + session.getSessionId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds(accessExpirationMs / 1000)
                .user(userMapper.toSummaryResponse(user))
                .build();
    }

    @Override
    @Transactional
    public CustomerLogoutResponse logoutCustomer(String bearerToken, CustomerLogoutRequest request, String clientIp) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new TokenValidationException("Bearer authorization token is required for customer logout");
        }

        String rawAccessToken = bearerToken.substring(7).trim();
        String callerPublicId = jwtTokenService.extractPublicId(rawAccessToken);
        String tokenSessionId = jwtTokenService.extractSessionId(rawAccessToken);

        String rawRefreshToken = request.getRefreshToken();
        String refreshHash = jwtTokenService.hashToken(rawRefreshToken);

        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByTokenHash(refreshHash);

        if (refreshTokenOpt.isPresent()) {
            RefreshToken refreshToken = refreshTokenOpt.get();
            // Verify ownership: refresh token must belong to the authenticated caller
            if (!refreshToken.getAuthUser().getPublicId().equals(callerPublicId)) {
                log.warn("Customer '{}' attempted to revoke refresh token belonging to another user", callerPublicId);
                throw new TokenValidationException("Refresh token does not belong to the authenticated customer");
            }

            // Verify session match if tokenSessionId is present
            if (tokenSessionId != null && !tokenSessionId.equals(refreshToken.getTokenFamilyId())) {
                log.warn("Refresh token family '{}' does not match access token session '{}'",
                        refreshToken.getTokenFamilyId(), tokenSessionId);
            }

            String effectiveSessionId = tokenSessionId != null ? tokenSessionId : refreshToken.getTokenFamilyId();
            sessionService.revokeSession(effectiveSessionId, "CUSTOMER_LOGOUT");
            refreshTokenRepository.revokeFamily(refreshToken.getTokenFamilyId(), LocalDateTime.now());

            auditService.logEvent(refreshToken.getAuthUser(), "CUSTOMER_LOGOUT", true, clientIp,
                    "Customer logged out from session: " + effectiveSessionId);
        } else {
            // Idempotent handling: If refresh token already removed/revoked, revoke session from access token
            if (tokenSessionId != null) {
                sessionService.revokeSession(tokenSessionId, "CUSTOMER_LOGOUT_IDEMPOTENT");
            }
        }

        return CustomerLogoutResponse.builder()
                .loggedOut(true)
                .build();
    }

    // ==============================================================================
    // Admin Login Workflow (Preserved with BCrypt & Lockout Throttling)
    // ==============================================================================

    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
    public AuthResponse loginAdmin(AdminLoginRequest request, String clientIp) {
        String email = request.getEmail().trim().toLowerCase();

        AdminAuth adminAuth = adminAuthRepository.findByEmail(email)
                .orElseThrow(() -> {
                    auditService.logEvent(null, "ADMIN_LOGIN_FAILED", false, clientIp, "Unknown admin email: " + email);
                    return new BadCredentialsException("Invalid email or password");
                });

        AuthUser authUser = adminAuth.getAuthUser();

        // Check Lockout
        LocalDateTime now = LocalDateTime.now();
        if (adminAuth.getLockedUntil() != null) {
            if (adminAuth.getLockedUntil().isAfter(now)) {
                long remainingMinutes = Duration.between(now, adminAuth.getLockedUntil()).toMinutes() + 1;
                auditService.logEvent(authUser, "ADMIN_LOGIN_LOCKED", false, clientIp, "Admin account locked for " + remainingMinutes + " mins");
                throw new AccountLockedException(
                        String.format("Account is temporarily locked due to excessive failed attempts. Please retry in %d minute(s).", remainingMinutes),
                        remainingMinutes);
            } else {
                adminAuth.setFailedLoginAttempts(0);
                adminAuth.setLockedUntil(null);
            }
        }

        if (authUser.getStatus() == UserStatus.BLOCKED || authUser.getStatus() == UserStatus.DISABLED) {
            auditService.logEvent(authUser, "ADMIN_LOGIN_FAILED", false, clientIp, "Admin account blocked or disabled");
            throw new BadCredentialsException("Admin account is not active");
        }

        // Verify Password
        if (!passwordEncoder.matches(request.getPassword(), adminAuth.getPasswordHash())) {
            int failedAttempts = adminAuth.getFailedLoginAttempts() + 1;
            adminAuth.setFailedLoginAttempts(failedAttempts);

            if (failedAttempts >= adminMaxFailedAttempts) {
                adminAuth.setLockedUntil(now.plusMinutes(adminLockoutMinutes));
                adminAuthRepository.save(adminAuth);
                auditService.logEvent(authUser, "ADMIN_LOCKOUT_TRIGGERED", false, clientIp,
                        "Admin account locked after " + failedAttempts + " attempts");
                throw new AccountLockedException(
                        String.format("Account locked due to %d consecutive failed login attempts. Retry in %d minutes.", failedAttempts, adminLockoutMinutes),
                        adminLockoutMinutes);
            }

            adminAuthRepository.save(adminAuth);
            int remaining = adminMaxFailedAttempts - failedAttempts;
            auditService.logEvent(authUser, "ADMIN_LOGIN_FAILED", false, clientIp,
                    "Invalid password attempt " + failedAttempts + " of " + adminMaxFailedAttempts);
            throw new BadCredentialsException(String.format("Invalid email or password. %d attempt(s) remaining before lockout.", remaining));
        }

        // Success
        adminAuth.setFailedLoginAttempts(0);
        adminAuth.setLockedUntil(null);
        adminAuthRepository.save(adminAuth);

        CustomerSession session = sessionService.createSession(authUser, "AdminPanel", clientIp);
        String accessToken = jwtTokenService.generateAccessToken(authUser, session.getSessionId());
        String refreshToken = jwtTokenService.createRefreshToken(authUser, session.getSessionId());

        auditService.logEvent(authUser, "ADMIN_LOGIN_SUCCESS", true, clientIp, "Admin authenticated successfully: " + email);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds(accessExpirationMs / 1000)
                .user(userMapper.toSummaryResponse(authUser))
                .build();
    }

    // ==============================================================================
    // Token Refresh & Legacy Endpoint Handlers
    // ==============================================================================

    @Override
    @Transactional(noRollbackFor = TokenValidationException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        return jwtTokenService.rotateRefreshToken(request.getRefreshToken());
    }

    @Override
    @Transactional
    public void logout(String authorizationHeader, String refreshToken) {
        jwtTokenService.revokeTokens(authorizationHeader, refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryResponse getCurrentUser(String identifier) {
        AuthUser user = adminAuthRepository.findByEmail(identifier)
                .map(AdminAuth::getAuthUser)
                .or(() -> customerAuthRepository.findByMobileNumber(normalizeMobile(identifier)).map(CustomerAuth::getUser))
                .or(() -> authUserRepository.findByPublicId(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + identifier));

        return userMapper.toSummaryResponse(user);
    }

    @Override
    @Transactional
    public OtpResponse requestOtp(SendOtpRequest request, String clientIp) {
        String destination = request.resolveDestination();
        String normalizedMobile = normalizeMobile(destination);
        OtpPurpose purpose = request.getPurpose();

        if (purpose == null) {
            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                purpose = OtpPurpose.REGISTRATION;
            } else if (customerAuthRepository.existsByMobileNumber(normalizedMobile)) {
                purpose = OtpPurpose.LOGIN;
            } else {
                purpose = OtpPurpose.REGISTRATION;
            }
        }

        if (purpose == OtpPurpose.LOGIN) {
            return sendCustomerLoginOtp(CustomerLoginSendOtpRequest.builder().mobileNumber(normalizedMobile).build(), clientIp);
        } else if (purpose == OtpPurpose.REGISTRATION) {
            String fullName = (request.getFullName() != null && !request.getFullName().isBlank()) ? request.getFullName().trim() : "Valued Customer";
            return sendCustomerRegistrationOtp(CustomerRegisterSendOtpRequest.builder()
                    .mobileNumber(normalizedMobile)
                    .fullName(fullName)
                    .email(request.getEmail())
                    .build(), clientIp);
        }
        return otpService.generateAndSendOtp(normalizedMobile, purpose, clientIp);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class, OtpExpiredException.class})
    public boolean verifyOtpOnly(VerifyOtpRequest request, String clientIp) {
        String destination = request.resolveDestination();
        return otpService.verifyOtp(destination, request.getOtp(), request.getPurpose(), clientIp);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class, OtpExpiredException.class, ServiceUnavailableException.class})
    public AuthResponse verifyOtpAndAuthenticate(VerifyOtpRequest request, String clientIp, String userAgent) {
        String destination = request.resolveDestination();
        String normalizedMobile = normalizeMobile(destination);

        OtpPurpose effectivePurpose = request.getPurpose();
        if (effectivePurpose == null) {
            if (otpService.getActiveChallenge(normalizedMobile, OtpPurpose.REGISTRATION).isPresent()) {
                effectivePurpose = OtpPurpose.REGISTRATION;
            } else if (otpService.getActiveChallenge(normalizedMobile, OtpPurpose.LOGIN).isPresent()) {
                effectivePurpose = OtpPurpose.LOGIN;
            } else {
                effectivePurpose = OtpPurpose.REGISTRATION;
            }
        }

        if (effectivePurpose == OtpPurpose.REGISTRATION) {
            return verifyCustomerRegistrationOtp(CustomerRegisterVerifyOtpRequest.builder()
                    .mobileNumber(normalizedMobile)
                    .otp(request.getOtp())
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .build(), clientIp, userAgent);
        } else if (effectivePurpose == OtpPurpose.LOGIN) {
            return verifyCustomerLoginOtp(CustomerLoginVerifyOtpRequest.builder()
                    .mobileNumber(normalizedMobile)
                    .otp(request.getOtp())
                    .build(), clientIp, userAgent);
        } else {
            otpService.verifyOtp(normalizedMobile, request.getOtp(), effectivePurpose, clientIp);
            return AuthResponse.builder().build();
        }
    }

    @Override
    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest request, String clientIp) {
        return verifyCustomerRegistrationOtp(CustomerRegisterVerifyOtpRequest.builder()
                .mobileNumber(request.getMobileNumber())
                .otp(request.getOtp())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .build(), clientIp, "WebClient");
    }

    @Override
    @Transactional
    public AuthResponse loginCustomer(CustomerLoginRequest request, String clientIp) {
        return verifyCustomerLoginOtp(CustomerLoginVerifyOtpRequest.builder()
                .mobileNumber(request.resolveDestination())
                .otp(request.getOtp())
                .build(), clientIp, "WebClient");
    }
}
