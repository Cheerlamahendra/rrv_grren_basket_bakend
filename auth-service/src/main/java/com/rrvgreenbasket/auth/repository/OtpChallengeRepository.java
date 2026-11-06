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
 */
@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByMobileNumberAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            String mobileNumber, OtpPurpose purpose);

    Optional<OtpChallenge> findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
            String mobileNumber, OtpPurpose purpose);
}
