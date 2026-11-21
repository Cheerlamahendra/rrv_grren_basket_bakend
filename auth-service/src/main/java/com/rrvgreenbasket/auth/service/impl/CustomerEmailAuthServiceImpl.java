package com.rrvgreenbasket.auth.service.impl;

import com.rrvgreenbasket.auth.client.CustomerServiceClient;
import com.rrvgreenbasket.auth.client.EmailProvider;
import com.rrvgreenbasket.auth.constants.AuthConstants;
import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.CustomerProfileDto;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.dto.response.PasswordResetTokenResponse;
import com.rrvgreenbasket.auth.entity.*;
import com.rrvgreenbasket.auth.enums.OtpPurpose;
import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import com.rrvgreenbasket.auth.exception.*;
import com.rrvgreenbasket.auth.mapper.UserMapper;
import com.rrvgreenbasket.auth.repository.*;
import com.rrvgreenbasket.auth.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * CustomerEmailAuthServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Implements all email-based customer authentication workflows:
 * <ol>
 *   <li><strong>Registration Phase 1</strong>: validate inputs, check uniqueness, BCrypt-hash
 *       password, stage data in OtpChallenge, dispatch OTP via Resend.</li>
 *   <li><strong>Registration Phase 2</strong>: verify OTP hash, create AuthUser + CustomerAuth (PENDING),
 *       call Customer Service to create profile, activate account, issue JWT tokens.</li>
 *   <li><strong>Email/Password Login</strong>: normalize email, BCrypt verify, account status checks,
 *       create session, issue tokens.</li>
 *   <li><strong>Forgot-Password Phase 1</strong>: generate PASSWORD_RESET OTP, dispatch via Resend.
 *       Always returns a consistent anti-enumeration response.</li>
 *   <li><strong>Forgot-Password Phase 2</strong>: verify OTP hash, issue opaque reset token (SHA-256 stored only).</li>
 *   <li><strong>Forgot-Password Phase 3</strong>: validate reset token, BCrypt-hash new password,
 *       revoke all sessions and refresh tokens, record audit event.</li>
 * </ol>
 *
 * <p><strong>Distributed Transaction Safety:</strong></p>
 * Auth Service and Customer Service have separate databases. The Registration Saga uses a PENDING
 * status gate: the account is activated only after Customer Service confirms profile creation.
 * If Customer Service fails, the account remains PENDING and no JWT tokens are issued.
 */
@Service
public class CustomerEmailAuthServiceImpl implements CustomerEmailAuthService {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailAuthServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;

    private final AuthUserRepository authUserRepository;
    private final CustomerAuthRepository customerAuthRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CustomerSessionRepository customerSessionRepository;
    private final EmailProvider emailProvider;
    private final JwtTokenService jwtTokenService;
    private final SessionService sessionService;
    private final CustomerServiceClient customerServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService auditService;
    private final UserMapper userMapper;

    private final int otpExpirationMinutes;
    @Value("${app.security.otp.log-to-console:false}")
    private boolean otpConsoleLogging;
    private final int otpResendCooldownSeconds;
    private final long accessExpirationMs;

    public CustomerEmailAuthServiceImpl(
            AuthUserRepository authUserRepository,
            CustomerAuthRepository customerAuthRepository,
            OtpChallengeRepository otpChallengeRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            CustomerSessionRepository customerSessionRepository,
            EmailProvider emailProvider,
            JwtTokenService jwtTokenService,
            SessionService sessionService,
            CustomerServiceClient customerServiceClient,
            PasswordEncoder passwordEncoder,
            AuthAuditService auditService,
            UserMapper userMapper,
            @Value("${app.otp.expiration-minutes:5}") int otpExpirationMinutes,
            @Value("${app.otp.resend-cooldown-seconds:60}") int otpResendCooldownSeconds,
            @Value("${app.jwt.access-token-expiration-ms:1800000}") long accessExpirationMs) {
        this.authUserRepository = authUserRepository;
        this.customerAuthRepository = customerAuthRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.customerSessionRepository = customerSessionRepository;
        this.emailProvider = emailProvider;
        this.jwtTokenService = jwtTokenService;
        this.sessionService = sessionService;
        this.customerServiceClient = customerServiceClient;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.userMapper = userMapper;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.otpResendCooldownSeconds = otpResendCooldownSeconds;
        this.accessExpirationMs = accessExpirationMs;
    }

    // ==========================================================================
    // REGISTRATION – Phase 1: Send OTP
    // ==========================================================================
    // Helper method for OTP console logging (dev only)
    private void logOtp(String purpose, String email, String rawOtp, int expiryMinutes) {
        if (otpConsoleLogging) {
            log.info("[DEV OTP] {} email={} otp={} expiresInMinutes={}", purpose, maskEmail(email), rawOtp, expiryMinutes);
        }
    }

    @Override
    @Transactional
    public OtpResponse sendRegistrationOtp(CustomerEmailRegisterSendOtpRequest request, String clientIp) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedMobile = normalizeMobile(request.getMobileNumber());

        // 1. Email uniqueness check
        customerAuthRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            if (existing.getUser().getStatus() == UserStatus.ACTIVE) {
                auditService.logEvent(existing.getUser(), "EMAIL_REG_OTP_REJECTED_DUPLICATE_EMAIL",
                        false, clientIp, "Duplicate email: " + maskEmail(normalizedEmail));
                throw new DuplicateResourceException(
                        "An account with this email address already exists. Please log in instead.");
            }
        });

        // 2. Mobile uniqueness check
        customerAuthRepository.findByMobileNumber(normalizedMobile).ifPresent(existing -> {
            if (existing.getUser().getStatus() == UserStatus.ACTIVE) {
                auditService.logEvent(existing.getUser(), "EMAIL_REG_OTP_REJECTED_DUPLICATE_MOBILE",
                        false, clientIp, "Duplicate mobile: " + normalizedMobile);
                throw new DuplicateResourceException(
                        "An account with this mobile number already exists. Please log in instead.");
            }
        });

        // 3. Cooldown check on email-based challenges
        otpChallengeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(
                normalizedEmail, OtpPurpose.REGISTRATION).ifPresent(latest -> {
            long elapsed = Duration.between(latest.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (elapsed < otpResendCooldownSeconds) {
                long remaining = otpResendCooldownSeconds - elapsed;
                throw new OtpCooldownException(
                        "Please wait " + remaining + " second(s) before requesting another OTP.", remaining);
            }
        });

        // 4. BCrypt hash the submitted password immediately — plaintext is never stored
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 5. Generate secure 6-digit OTP and hash it
        String rawOtp = generateRawOtp();
        // Log OTP to console for development if enabled
        logOtp("REGISTRATION", normalizedEmail, rawOtp, otpExpirationMinutes);
        String otpHash = passwordEncoder.encode(rawOtp);

        // 6. Invalidate any previous unconsumed challenges for this email+purpose
        otpChallengeRepository.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
                normalizedEmail, OtpPurpose.REGISTRATION).ifPresent(c -> {
            c.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(c);
        });

        // 7. Stage registration data in OtpChallenge
        OtpChallenge challenge = OtpChallenge.builder()
                .email(normalizedEmail)
                .fullName(request.getFullName().trim())
                .pendingMobileNumber(normalizedMobile)
                .pendingPasswordHash(passwordHash)
                .purpose(OtpPurpose.REGISTRATION)
                .otpHash(otpHash)
                .attempts(0)
                .maxAttempts(3)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .requestedIp(clientIp)
                .build();
        otpChallengeRepository.save(challenge);

        // 8. Send OTP email via Resend (rawOtp is NEVER logged)
        var result = emailProvider.sendOtpEmail(normalizedEmail, rawOtp, otpExpirationMinutes);
        if (!result.isDelivered()) {
            log.warn("[EMAIL_REG] OTP email dispatch non-delivery for: {}", maskEmail(normalizedEmail));
        }

        auditService.logEvent(null, "EMAIL_REG_OTP_SENT", true, clientIp,
                "Registration OTP dispatched to " + maskEmail(normalizedEmail));

        return OtpResponse.builder()
                .email(normalizedEmail)
                .expiresInMinutes(otpExpirationMinutes)
                .cooldownSeconds(otpResendCooldownSeconds)
                .message("A verification OTP has been sent to your email address. Valid for "
                        + otpExpirationMinutes + " minute(s).")
                .build();
    }

    // ==========================================================================
    // REGISTRATION – Phase 2: Verify OTP & Complete Registration
    // ==========================================================================

    @Override
    @Transactional
    public AuthResponse verifyRegistrationOtp(CustomerEmailRegisterVerifyOtpRequest request,
                                               String clientIp, String userAgent) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        // 1. Load active challenge
        OtpChallenge challenge = otpChallengeRepository
                .findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
                        normalizedEmail, OtpPurpose.REGISTRATION)
                .orElseThrow(() -> new InvalidOtpException(
                        "No active registration OTP found. Please request a new OTP."));

        // 2. Expiry
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            challenge.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(challenge);
            throw new OtpExpiredException("OTP has expired. Please request a new code.");
        }

        // 3. Max attempts
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            challenge.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(challenge);
            throw new InvalidOtpException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        // 4. BCrypt verify OTP
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (!passwordEncoder.matches(request.getOtp(), challenge.getOtpHash())) {
            otpChallengeRepository.save(challenge);
            int remaining = challenge.getMaxAttempts() - challenge.getAttempts();
            if (remaining <= 0) {
                challenge.setUsedAt(LocalDateTime.now());
                otpChallengeRepository.save(challenge);
                throw new InvalidOtpException("Maximum OTP attempts exceeded. Please request a new OTP.");
            }
            throw new InvalidOtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // 5. Post-verification uniqueness guards (race condition protection)
        customerAuthRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            if (existing.getUser().getStatus() == UserStatus.ACTIVE) {
                challenge.setUsedAt(LocalDateTime.now());
                otpChallengeRepository.save(challenge);
                throw new DuplicateResourceException(
                        "An account with this email already exists. Please log in.");
            }
        });

        String pendingMobile = challenge.getPendingMobileNumber();
        if (pendingMobile != null) {
            customerAuthRepository.findByMobileNumber(pendingMobile).ifPresent(existing -> {
                if (existing.getUser().getStatus() == UserStatus.ACTIVE) {
                    challenge.setUsedAt(LocalDateTime.now());
                    otpChallengeRepository.save(challenge);
                    throw new DuplicateResourceException(
                            "An account with this mobile number already exists. Please log in.");
                }
            });
        }

        // 6. Mark OTP as consumed
        challenge.setUsedAt(LocalDateTime.now());
        otpChallengeRepository.save(challenge);

        // 7. Create AuthUser in PENDING state
        LocalDateTime now = LocalDateTime.now();
        String publicId = UUID.randomUUID().toString();

        AuthUser authUser = AuthUser.builder()
                .publicId(publicId)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        CustomerAuth customerAuth = CustomerAuth.builder()
                .user(authUser)
                .mobileNumber(pendingMobile != null ? pendingMobile : "")
                .mobileVerified(false)
                .email(normalizedEmail)
                .passwordHash(challenge.getPendingPasswordHash())
                .emailVerified(true)
                .emailVerifiedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        authUser.setCustomerAuth(customerAuth);
        authUser = authUserRepository.save(authUser);
        log.info("[EMAIL_REG] AuthUser staged PENDING publicId='{}' email='{}'",
                publicId, maskEmail(normalizedEmail));

        // 8. Initialize customer profile in Customer Service (distributed saga)
        boolean profileCreated = false;
        try {
            profileCreated = customerServiceClient.initializeCustomerProfile(
                    authUser.getPublicId(),
                    challenge.getFullName(),
                    pendingMobile,
                    normalizedEmail);
        } catch (Exception e) {
            log.error("[EMAIL_REG] Customer Service call failed for publicId='{}': {}", publicId, e.getMessage());
        }

        // Probe: check if profile was created despite exception (idempotency)
        if (!profileCreated) {
            try {
                profileCreated = customerServiceClient.verifyProfileExists(authUser.getPublicId());
            } catch (Exception e) {
                log.warn("[EMAIL_REG] Profile probe also failed for publicId='{}': {}", publicId, e.getMessage());
            }
        }

        // 9. Gate: account stays PENDING, no tokens issued if profile creation failed
        if (!profileCreated) {
            log.error("[EMAIL_REG] Profile provisioning failed for publicId='{}'. Account remains PENDING.", publicId);
            auditService.logEvent(authUser, "EMAIL_REG_PROFILE_FAILED", false, clientIp,
                    "Customer Service profile creation failed. Account kept PENDING.");
            throw new ServiceUnavailableException(
                    "Account created but profile provisioning failed. " +
                    "Please retry registration or contact support. Your email is reserved.");
        }

        // 10. Activate account
        authUser.setStatus(UserStatus.ACTIVE);
        authUser.setUpdatedAt(LocalDateTime.now());
        authUserRepository.save(authUser);
        log.info("[EMAIL_REG] Account ACTIVATED publicId='{}'", publicId);

        // 11. Create session and issue JWT tokens
        CustomerSession session = sessionService.createSession(authUser, userAgent, clientIp);
        String accessToken = jwtTokenService.generateAccessToken(authUser, session.getSessionId());
        String refreshToken = jwtTokenService.createRefreshToken(authUser, session.getSessionId());

        auditService.logEvent(authUser, "EMAIL_REG_COMPLETE", true, clientIp,
                "Registration complete: " + maskEmail(normalizedEmail));

        String fullName = challenge.getFullName();
        if (fullName == null || fullName.isBlank()) {
            try {
                CustomerProfileDto customerProfile = customerServiceClient.getCustomerProfile(authUser.getPublicId());
                if (customerProfile != null && customerProfile.getFullName() != null) {
                    fullName = customerProfile.getFullName();
                }
            } catch (Exception e) {
                log.warn("[EMAIL_REG] Could not fetch profile to populate fullName: {}", e.getMessage());
            }
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds(accessExpirationMs / 1000)
                .user(userMapper.toSummaryResponse(authUser, fullName))
                .build();
    }

    // ==========================================================================
    // LOGIN – Email + Password
    // ==========================================================================

    @Override
    @Transactional
    public AuthResponse emailPasswordLogin(CustomerEmailPasswordLoginRequest request,
                                            String clientIp, String userAgent) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        // 1. Load customer auth record — use generic error to prevent user enumeration
        CustomerAuth customerAuth = customerAuthRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("[EMAIL_LOGIN] No account found for: {}", maskEmail(normalizedEmail));
                    return new ResourceNotFoundException("Invalid email or password.");
                });

        AuthUser authUser = customerAuth.getUser();

        // 2. BCrypt verify password
        if (customerAuth.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), customerAuth.getPasswordHash())) {
            auditService.logEvent(authUser, "EMAIL_LOGIN_BAD_CREDENTIALS", false, clientIp,
                    "Bad credentials: " + maskEmail(normalizedEmail));
            throw new ResourceNotFoundException("Invalid email or password.");
        }

        // 3. Email verification check
        if (!customerAuth.isEmailVerified()) {
            auditService.logEvent(authUser, "EMAIL_LOGIN_UNVERIFIED", false, clientIp,
                    "Unverified email: " + maskEmail(normalizedEmail));
            throw new AccountLockedException("Email not verified. Please complete registration.");
        }

        // 4. Account status gate
        switch (authUser.getStatus()) {
            case PENDING -> throw new AccountLockedException(
                    "Account setup is incomplete. Please retry registration.");
            case BLOCKED -> {
                auditService.logEvent(authUser, "EMAIL_LOGIN_BLOCKED", false, clientIp,
                        "BLOCKED account login attempt: " + maskEmail(normalizedEmail));
                throw new AccountLockedException("Your account has been blocked. Please contact support.");
            }
            case DISABLED -> {
                auditService.logEvent(authUser, "EMAIL_LOGIN_DISABLED", false, clientIp,
                        "DISABLED account login attempt: " + maskEmail(normalizedEmail));
                throw new AccountLockedException("Your account is disabled. Please contact support.");
            }
            case ACTIVE -> { /* proceed */ }
        }

        // 5. Create session and issue tokens
        CustomerSession session = sessionService.createSession(authUser, userAgent, clientIp);
        String accessToken = jwtTokenService.generateAccessToken(authUser, session.getSessionId());
        String refreshToken = jwtTokenService.createRefreshToken(authUser, session.getSessionId());

        auditService.logEvent(authUser, "EMAIL_LOGIN_SUCCESS", true, clientIp,
                "Email/password login: " + maskEmail(normalizedEmail));
        log.info("[EMAIL_LOGIN] Success publicId='{}' email='{}'",
                authUser.getPublicId(), maskEmail(normalizedEmail));

        String fullName = null;
        try {
            CustomerProfileDto customerProfile = customerServiceClient.getCustomerProfile(authUser.getPublicId());
            if (customerProfile != null) {
                fullName = customerProfile.getFullName();
            }
        } catch (Exception e) {
            log.warn("[EMAIL_LOGIN] Could not retrieve customer profile for fullName: {}", e.getMessage());
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds(accessExpirationMs / 1000)
                .user(userMapper.toSummaryResponse(authUser, fullName))
                .build();
    }

    // ==========================================================================
    // FORGOT PASSWORD – Phase 1: Send OTP
    // ==========================================================================

    @Override
    @Transactional
    public OtpResponse sendForgotPasswordOtp(CustomerForgotPasswordSendOtpRequest request, String clientIp) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        // Verify that the email exists; if not, respond with 404
        CustomerAuth customerAuth = customerAuthRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Email address is not registered. Please check your email."));
        AuthUser authUser = customerAuth.getUser();
        if (authUser.getStatus() == UserStatus.ACTIVE && customerAuth.isEmailVerified()) {
            // Cooldown check
            Optional<OtpChallenge> latest = otpChallengeRepository
                    .findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalizedEmail, OtpPurpose.PASSWORD_RESET);
            if (latest.isPresent()) {
                long elapsed = Duration.between(latest.get().getCreatedAt(), LocalDateTime.now()).getSeconds();
                if (elapsed < otpResendCooldownSeconds) {
                    log.info("[FORGOT_PWD] Cooldown active for: {}", maskEmail(normalizedEmail));
                    // Proceed without throwing to keep anti-enumeration for cooldown
                }
            }

            // Invalidate previous unconsumed reset OTP
            otpChallengeRepository.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
                    normalizedEmail, OtpPurpose.PASSWORD_RESET).ifPresent(c -> {
                c.setUsedAt(LocalDateTime.now());
                otpChallengeRepository.save(c);
            });

            String rawOtp = generateRawOtp();
            // Log OTP for dev if enabled
            logOtp("FORGOT_PASSWORD", normalizedEmail, rawOtp, otpExpirationMinutes);
            String otpHash = passwordEncoder.encode(rawOtp);

            OtpChallenge challenge = OtpChallenge.builder()
                    .email(normalizedEmail)
                    .purpose(OtpPurpose.PASSWORD_RESET)
                    .otpHash(otpHash)
                    .attempts(0)
                    .maxAttempts(3)
                    .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                    .requestedIp(clientIp)
                    .build();
            otpChallengeRepository.save(challenge);

            emailProvider.sendOtpEmail(normalizedEmail, rawOtp, otpExpirationMinutes);

            auditService.logEvent(authUser, "FORGOT_PWD_OTP_SENT", true, clientIp,
                    "Password reset OTP sent to: " + maskEmail(normalizedEmail));
        } else {
            log.info("[FORGOT_PWD] Skipped – account not eligible: {}", maskEmail(normalizedEmail));
        }

        // Consistent response
        return OtpResponse.builder()
                .expiresInMinutes(otpExpirationMinutes)
                .cooldownSeconds(otpResendCooldownSeconds)
                .message("If an account with this email exists and is verified, " +
                        "a password reset OTP has been sent.")
                .build();
    }

    // ==========================================================================
    // FORGOT PASSWORD – Phase 2: Verify OTP → Issue Reset Token
    // ==========================================================================

    @Override
    @Transactional
    public PasswordResetTokenResponse verifyForgotPasswordOtp(
            CustomerForgotPasswordVerifyOtpRequest request, String clientIp) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        OtpChallenge challenge = otpChallengeRepository
                .findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
                        normalizedEmail, OtpPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidOtpException(
                        "No active password reset OTP found. Please request a new OTP."));

        // Expiry
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            challenge.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(challenge);
            throw new OtpExpiredException("OTP has expired. Please request a new password reset OTP.");
        }

        // Max attempts
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            challenge.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(challenge);
            throw new InvalidOtpException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        // BCrypt verify OTP
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (!passwordEncoder.matches(request.getOtp(), challenge.getOtpHash())) {
            otpChallengeRepository.save(challenge);
            int remaining = challenge.getMaxAttempts() - challenge.getAttempts();
            if (remaining <= 0) {
                challenge.setUsedAt(LocalDateTime.now());
                otpChallengeRepository.save(challenge);
                throw new InvalidOtpException("Maximum OTP attempts exceeded. Please request a new OTP.");
            }
            throw new InvalidOtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Mark OTP consumed
        challenge.setUsedAt(LocalDateTime.now());
        otpChallengeRepository.save(challenge);

        // Load customer
        CustomerAuth customerAuth = customerAuthRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        AuthUser authUser = customerAuth.getUser();

        // Generate opaque reset token: UUID-based, only SHA-256 hash stored
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        String tokenHash = sha256Hex(rawToken);

        // Revoke any existing unused reset tokens for this user
        passwordResetTokenRepository.revokeAllActiveByUserId(authUser.getId(), LocalDateTime.now());

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .authUser(authUser)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES))
                .build();
        passwordResetTokenRepository.save(resetToken);

        auditService.logEvent(authUser, "FORGOT_PWD_OTP_VERIFIED", true, clientIp,
                "Password reset token issued: " + maskEmail(normalizedEmail));

        return PasswordResetTokenResponse.builder()
                .resetToken(rawToken)
                .expiresInMinutes(RESET_TOKEN_EXPIRY_MINUTES)
                .message("OTP verified. Use the reset token to set your new password within "
                        + RESET_TOKEN_EXPIRY_MINUTES + " minutes.")
                .build();
    }

    // ==========================================================================
    // FORGOT PASSWORD – Phase 3: Reset Password
    // ==========================================================================

    @Override
    @Transactional
    public void resetPassword(CustomerForgotPasswordResetRequest request, String clientIp) {

        // 1. Password confirmation match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("New password and confirm password do not match.");
        }

        // 2. Hash token and look up
        String tokenHash = sha256Hex(request.getResetToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenValidationException(
                        "Invalid or expired password reset token."));

        // 3. Expiry check
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenValidationException(
                    "Password reset token has expired. Please restart the forgot-password process.");
        }

        // 4. Single-use check
        if (resetToken.getUsedAt() != null) {
            throw new TokenValidationException("Password reset token has already been used.");
        }

        AuthUser authUser = resetToken.getAuthUser();
        CustomerAuth customerAuth = customerAuthRepository.findById(authUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));

        // 5. BCrypt new password
        customerAuth.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        customerAuth.setUpdatedAt(LocalDateTime.now());
        customerAuthRepository.save(customerAuth);

        // 6. Mark reset token consumed
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        // 7. Revoke all refresh tokens and sessions (security policy: force re-login)
        refreshTokenRepository.revokeAllByUserId(authUser.getId(), LocalDateTime.now());
        customerSessionRepository.revokeAllActiveByUserId(authUser.getId(), LocalDateTime.now());

        auditService.logEvent(authUser, "PASSWORD_RESET_COMPLETE", true, clientIp,
                "Password reset complete for publicId='" + authUser.getPublicId() + "'");
        log.info("[FORGOT_PWD] Password reset complete for publicId='{}'", authUser.getPublicId());
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    private String generateRawOtp() {
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null) return null;
        String cleaned = mobile.replaceAll("[^0-9+]", "").trim();
        if (cleaned.startsWith("+91")) return cleaned;
        if (cleaned.length() == 10) return "+91" + cleaned;
        if (!cleaned.startsWith("+")) return "+" + cleaned;
        return cleaned;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String user = email.substring(0, at);
        String domain = email.substring(at);
        if (user.length() <= 2) return user.charAt(0) + "***" + domain;
        return user.charAt(0) + "***" + user.charAt(user.length() - 1) + domain;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
