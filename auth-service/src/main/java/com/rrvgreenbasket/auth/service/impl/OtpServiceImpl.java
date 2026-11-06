package com.rrvgreenbasket.auth.service.impl;

import com.rrvgreenbasket.auth.client.EmailProvider;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.entity.OtpChallenge;
import com.rrvgreenbasket.auth.enums.OtpPurpose;
import com.rrvgreenbasket.auth.exception.InvalidOtpException;
import com.rrvgreenbasket.auth.exception.OtpCooldownException;
import com.rrvgreenbasket.auth.exception.OtpExpiredException;
import com.rrvgreenbasket.auth.repository.OtpChallengeRepository;
import com.rrvgreenbasket.auth.service.AuthAuditService;
import com.rrvgreenbasket.auth.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * OtpServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Manages OTP challenges backed by MySQL table <code>otp_challenges</code>.
 * Enforces rate limiting, BCrypt hashing, attempt limits, metadata association, and single-use verification.
 */
@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpChallengeRepository otpChallengeRepository;
    private final EmailProvider emailProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService auditService;

    private final int expirationMinutes;
    private final int maxAttempts;
    private final int resendCooldownSeconds;

    public OtpServiceImpl(
            OtpChallengeRepository otpChallengeRepository,
            EmailProvider emailProvider,
            PasswordEncoder passwordEncoder,
            AuthAuditService auditService,
            @Value("${app.otp.expiration-minutes:5}") int expirationMinutes,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.resend-cooldown-seconds:60}") int resendCooldownSeconds) {
        this.otpChallengeRepository = otpChallengeRepository;
        this.emailProvider = emailProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.expirationMinutes = expirationMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    private String normalizeMobileNumber(String input) {
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

    @Override
    @Transactional
    public OtpResponse generateAndSendOtp(String mobileNumber, OtpPurpose purpose, String clientIp) {
        return generateAndSendOtp(mobileNumber, purpose, clientIp, null, null);
    }

    @Override
    @Transactional
    public OtpResponse generateAndSendOtp(String mobileNumber, OtpPurpose purpose, String clientIp, String fullName, String email) {
        String normalizedMobile = normalizeMobileNumber(mobileNumber);

        // 1. Cooldown Check
        Optional<OtpChallenge> latestChallenge = otpChallengeRepository
                .findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(normalizedMobile, purpose);

        if (latestChallenge.isPresent()) {
            LocalDateTime lastCreatedAt = latestChallenge.get().getCreatedAt();
            long elapsedSeconds = Duration.between(lastCreatedAt, LocalDateTime.now()).getSeconds();
            if (elapsedSeconds < resendCooldownSeconds) {
                long remainingSeconds = resendCooldownSeconds - elapsedSeconds;
                throw new OtpCooldownException(
                        String.format("Please wait %d second(s) before requesting another OTP", remainingSeconds),
                        remainingSeconds);
            }
        }

        // 2. Generate 6-digit numeric OTP
        int numericOtp = 100000 + SECURE_RANDOM.nextInt(900000);
        String rawOtp = String.valueOf(numericOtp);
        log.info("Generated OTP for {}: {}", maskMobile(normalizedMobile), rawOtp);
        // 3. BCrypt Hash OTP
        String otpHash = passwordEncoder.encode(rawOtp);

        // 4. Invalidate previous unconsumed challenges
        otpChallengeRepository.findFirstByMobileNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(normalizedMobile, purpose)
                .ifPresent(existing -> {
                    existing.setUsedAt(LocalDateTime.now());
                    otpChallengeRepository.save(existing);
                });

        // 5. Persist OtpChallenge entity with staged registration metadata
        OtpChallenge challenge = OtpChallenge.builder()
                .mobileNumber(normalizedMobile)
                .purpose(purpose)
                .otpHash(otpHash)
                .attempts(0)
                .maxAttempts(maxAttempts)
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .createdAt(LocalDateTime.now())
                .requestedIp(clientIp)
                .fullName(fullName != null ? fullName.trim() : null)
                .email(email != null && !email.isBlank() ? email.trim() : null)
                .build();
        otpChallengeRepository.save(challenge);

        // 6. Dispatch Notification via Provider
        emailProvider.sendOtpEmail(normalizedMobile, rawOtp, expirationMinutes);

        // 7. Audit Log Event
        auditService.logEvent(null, "OTP_DISPATCHED", true, clientIp,
                "OTP requested for " + maskMobile(normalizedMobile) + ", purpose=" + purpose);

        return OtpResponse.builder()
                .email(normalizedMobile)
                .expiresInMinutes(expirationMinutes)
                .cooldownSeconds(resendCooldownSeconds)
                .message("Verification OTP has been dispatched to " + maskMobile(normalizedMobile))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = {InvalidOtpException.class, OtpExpiredException.class})
    public boolean verifyOtp(String mobileNumber, String rawOtp, OtpPurpose purpose, String clientIp) {
        String normalizedMobile = normalizeMobileNumber(mobileNumber);

        OtpChallenge challenge = otpChallengeRepository
                .findFirstByMobileNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(normalizedMobile, purpose)
                .orElseThrow(() -> {
                    auditService.logEvent(null, "OTP_VERIFY_FAILED", false, clientIp, "No active challenge for " + maskMobile(normalizedMobile));
                    return new InvalidOtpException("No active OTP request found. Please request a new OTP.");
                });

        // 1. Expiry Check
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            challenge.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(challenge);
            auditService.logEvent(null, "OTP_EXPIRED", false, clientIp, "Expired challenge for " + maskMobile(normalizedMobile));
            throw new OtpExpiredException("OTP has expired. Please request a new code.");
        }

        // 2. Max Attempts Check
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            challenge.setUsedAt(LocalDateTime.now());
            otpChallengeRepository.save(challenge);
            auditService.logEvent(null, "OTP_ATTEMPTS_EXHAUSTED", false, clientIp, "Max attempts reached for " + maskMobile(normalizedMobile));
            throw new InvalidOtpException("Maximum OTP verification attempts exceeded. Please request a new OTP.");
        }

        // 3. Increment Attempts
        challenge.setAttempts(challenge.getAttempts() + 1);

        // 4. Verify BCrypt Hash
        if (!passwordEncoder.matches(rawOtp, challenge.getOtpHash())) {
            otpChallengeRepository.save(challenge);
            int remaining = challenge.getMaxAttempts() - challenge.getAttempts();
            auditService.logEvent(null, "OTP_MISMATCH", false, clientIp,
                    "Invalid OTP entered for " + maskMobile(normalizedMobile) + ". Remaining: " + remaining);
            if (remaining <= 0) {
                challenge.setUsedAt(LocalDateTime.now());
                otpChallengeRepository.save(challenge);
                throw new InvalidOtpException("Maximum OTP verification attempts exceeded. Please request a new OTP.");
            }
            throw new InvalidOtpException(String.format("Invalid OTP code. %d attempt(s) remaining.", remaining));
        }

        // 5. Mark Used
        challenge.setUsedAt(LocalDateTime.now());
        otpChallengeRepository.save(challenge);

        auditService.logEvent(null, "OTP_VERIFIED", true, clientIp, "OTP verified for " + maskMobile(normalizedMobile));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpChallenge> getActiveChallenge(String mobileNumber, OtpPurpose purpose) {
        String normalizedMobile = normalizeMobileNumber(mobileNumber);
        return otpChallengeRepository.findFirstByMobileNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(normalizedMobile, purpose);
    }

    @Override
    @Transactional
    public void resetAttempts(String mobileNumber, OtpPurpose purpose) {
        String normalizedMobile = normalizeMobileNumber(mobileNumber);
        otpChallengeRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(normalizedMobile, purpose)
                .ifPresent(challenge -> {
                    challenge.setAttempts(Math.max(0, challenge.getAttempts() - 1));
                    challenge.setUsedAt(null);
                    otpChallengeRepository.save(challenge);
                });
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 3);
    }
}
