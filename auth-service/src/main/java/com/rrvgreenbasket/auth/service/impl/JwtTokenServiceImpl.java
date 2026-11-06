package com.rrvgreenbasket.auth.service.impl;

import com.rrvgreenbasket.auth.constants.AuthConstants;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.SessionValidationResponse;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.entity.RefreshToken;
import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.exception.TokenValidationException;
import com.rrvgreenbasket.auth.mapper.UserMapper;
import com.rrvgreenbasket.auth.repository.RefreshTokenRepository;
import com.rrvgreenbasket.auth.service.AuthAuditService;
import com.rrvgreenbasket.auth.service.JwtTokenService;
import com.rrvgreenbasket.auth.service.SessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JwtTokenServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Implements HMAC-SHA256 JWT access token creation, family-based refresh token rotation,
 * canonical claim binding, session association, and real-time invalidation.
 */
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenServiceImpl.class);

    private final String secret;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final String issuer;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final AuthAuditService auditService;
    private final SessionService sessionService;

    public JwtTokenServiceImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms:1800000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshExpirationMs, // 7 days
            @Value("${app.jwt.issuer:rrv-green-basket-auth}") String issuer,
            RefreshTokenRepository refreshTokenRepository,
            UserMapper userMapper,
            AuthAuditService auditService,
            @Lazy SessionService sessionService) {
        this.secret = secret;
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.issuer = issuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.sessionService = sessionService;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateAccessToken(AuthUser user) {
        return generateAccessToken(user, UUID.randomUUID().toString());
    }

    @Override
    public String generateAccessToken(AuthUser user, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpirationMs);

        String roleName;
        if (user.getRole() == UserRole.CUSTOMER || user.getRole() == UserRole.USER) {
            roleName = "ROLE_CUSTOMER";
        } else {
            roleName = "ROLE_" + user.getRole().name();
        }

        String mobileNumber = user.getCustomerAuth() != null ? user.getCustomerAuth().getMobileNumber() : null;
        String email = user.getAdminAuth() != null ? user.getAdminAuth().getEmail() : null;

        var builder = Jwts.builder()
                .subject(user.getPublicId())
                .id(UUID.randomUUID().toString()) // jti
                .claim(AuthConstants.CLAIM_PUBLIC_ID, user.getPublicId())
                .claim("userId", user.getPublicId())
                .claim("user_id", user.getId())
                .claim("role", roleName)
                .claim(AuthConstants.CLAIM_ROLES, Collections.singletonList(roleName))
                .claim("status", user.getStatus().name())
                .claim("sid", sessionId != null ? sessionId : UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey());

        if (mobileNumber != null) {
            builder.claim("mobile_number", mobileNumber);
        }
        if (email != null) {
            builder.claim(AuthConstants.CLAIM_EMAIL, email);
        }

        return builder.compact();
    }

    @Override
    @Transactional
    public String createRefreshToken(AuthUser user) {
        return createRefreshToken(user, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public String createRefreshToken(AuthUser user, String sessionId) {
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        String tokenFamilyId = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .authUser(user)
                .tokenHash(tokenHash)
                .tokenFamilyId(tokenFamilyId)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Override
    @Transactional(noRollbackFor = TokenValidationException.class)
    public AuthResponse rotateRefreshToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    auditService.logEvent(null, "TOKEN_ROTATION_FAILED", false, null, "Refresh token hash not found");
                    return new TokenValidationException("Invalid refresh token");
                });

        // 1. Replay Attack Detection: If token is already revoked, compromise detected!
        if (existing.getRevokedAt() != null) {
            log.warn("Revoked refresh token reuse detected for familyId: {}. Invalidating token family and session.",
                    existing.getTokenFamilyId());
            refreshTokenRepository.revokeFamily(existing.getTokenFamilyId(), LocalDateTime.now());
            sessionService.revokeSession(existing.getTokenFamilyId(), "REFRESH_TOKEN_REUSE_DETECTED");
            auditService.logEvent(existing.getAuthUser(), "TOKEN_REUSE_DETECTED", false, null,
                    "Revoked token reused. Entire family " + existing.getTokenFamilyId() + " and session invalidated.");
            throw new TokenValidationException("Revoked refresh token reuse detected. All sessions in this token family have been invalidated.");
        }

        // 2. Expiration Check
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(existing);
            auditService.logEvent(existing.getAuthUser(), "TOKEN_EXPIRED", false, null, "Expired refresh token submitted");
            throw new TokenValidationException("Refresh token has expired. Please login again.");
        }

        // 3. Session Active Check
        SessionValidationResponse sessionStatus = sessionService.validateSession(existing.getTokenFamilyId());
        if (!sessionStatus.isValid()) {
            existing.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(existing);
            auditService.logEvent(existing.getAuthUser(), "SESSION_INVALID_REFRESH", false, null, "Session is revoked or expired");
            throw new TokenValidationException("Session has been revoked or expired. Please login again.");
        }

        AuthUser user = existing.getAuthUser();

        // 4. Issue Rotated Token within same family (session)
        String newRawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawRefreshToken);

        RefreshToken replacementToken = RefreshToken.builder()
                .authUser(user)
                .tokenHash(newTokenHash)
                .tokenFamilyId(existing.getTokenFamilyId())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .createdAt(LocalDateTime.now())
                .build();

        RefreshToken savedReplacement = refreshTokenRepository.save(replacementToken);

        // Invalidate previous token and link replacement
        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedByToken(savedReplacement);
        refreshTokenRepository.save(existing);

        // Touch session
        sessionService.touchSession(existing.getTokenFamilyId());

        String newAccessToken = generateAccessToken(user, existing.getTokenFamilyId());

        auditService.logEvent(user, "TOKEN_ROTATED", true, null, "Refresh token rotated successfully for family: " + existing.getTokenFamilyId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds(accessExpirationMs / 1000)
                .user(userMapper.toSummaryResponse(user))
                .build();
    }

    @Override
    @Transactional
    public void revokeTokens(String rawAccessToken, String rawRefreshToken) {
        String sessionId = null;
        if (rawAccessToken != null && !rawAccessToken.isBlank()) {
            try {
                sessionId = extractSessionId(rawAccessToken);
            } catch (Exception e) {
                log.debug("Could not extract sessionId from rawAccessToken: {}", e.getMessage());
            }
        }

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String refreshHash = hashToken(rawRefreshToken);
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(refreshHash);
            if (tokenOpt.isPresent()) {
                RefreshToken token = tokenOpt.get();
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
                if (sessionId == null) {
                    sessionId = token.getTokenFamilyId();
                }
            }
        }

        if (sessionId != null) {
            revokeSession(sessionId, "LOGOUT");
        }
    }

    @Override
    @Transactional
    public void revokeSession(String sessionId, String reason) {
        sessionService.revokeSession(sessionId, reason);
    }

    @Override
    public boolean validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            Claims claims = parseClaims(token);
            if (claims.getExpiration().before(new Date())) {
                return false;
            }

            // Real-time authoritative session validation
            String sessionId = claims.get("sid", String.class);
            if (sessionId != null) {
                SessionValidationResponse sessionStatus = sessionService.validateSession(sessionId);
                if (!sessionStatus.isValid()) {
                    log.warn("Access token rejected: underlying session '{}' is revoked or invalid", sessionId);
                    return false;
                }
            }

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractIdentifier(String token) {
        Claims claims = parseClaims(token);
        String mobile = claims.get("mobile_number", String.class);
        if (mobile != null) {
            return mobile;
        }
        String email = claims.get(AuthConstants.CLAIM_EMAIL, String.class);
        if (email != null) {
            return email;
        }
        return claims.getSubject();
    }

    @Override
    public String extractPublicId(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public String extractSessionId(String token) {
        return parseClaims(token).get("sid", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
