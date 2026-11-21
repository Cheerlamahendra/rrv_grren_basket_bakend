package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.OtpChallenge;
import com.rrvgreenbasket.auth.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * OtpChallengeRepository
 *
 * <p><strong>What it does:</strong></p>
 * Queries and updates OTP challenge records in <code>otp_challenges</code>.
 * Supports both legacy mobile-number-based lookups and new email-based lookups (V3).
 */
@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    // -------------------------------------------------------------------------
    // Mobile-based lookups (legacy flows)
    // -------------------------------------------------------------------------

    Optional<OtpChallenge> findFirstByMobileNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            String mobileNumber, OtpPurpose purpose);

    Optional<OtpChallenge> findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
            String mobileNumber, OtpPurpose purpose);

    // -------------------------------------------------------------------------
    // Email-based lookups (V3: email registration, forgot-password)
    // -------------------------------------------------------------------------

    Optional<OtpChallenge> findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    Optional<OtpChallenge> findFirstByEmailAndPurposeOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);
}
