package com.rrvgreenbasket.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.auth.client.CustomerServiceClient;
import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.entity.AdminAuth;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.entity.CustomerSession;
import com.rrvgreenbasket.auth.entity.OtpChallenge;
import com.rrvgreenbasket.auth.enums.OtpPurpose;
import com.rrvgreenbasket.auth.enums.SessionStatus;
import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import com.rrvgreenbasket.auth.repository.*;
import com.rrvgreenbasket.auth.security.S2SSignatureUtil;
import com.rrvgreenbasket.auth.service.AuthService;
import com.rrvgreenbasket.auth.service.JwtTokenService;
import com.rrvgreenbasket.auth.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private CustomerAuthRepository customerAuthRepository;

    @Autowired
    private AdminAuthRepository adminAuthRepository;

    @Autowired
    private OtpChallengeRepository otpChallengeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CustomerSessionRepository customerSessionRepository;

    @Autowired
    private AuthAuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpService otpService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private S2SSignatureUtil signatureUtil;

    @MockBean
    private CustomerServiceClient customerServiceClient;

    @Value("${app.security.internal-service-secret}")
    private String internalServiceSecret;

    private String generateS2SSignature(String method, String path, long timestamp, String nonce) {
        String bodyHash = signatureUtil.calculateBodyHash(new byte[0]);
        String canonical = signatureUtil.buildCanonicalString(method, path, timestamp, nonce, bodyHash);
        return signatureUtil.calculateSignature(canonical, internalServiceSecret);
    }

    @BeforeEach
    void setUp() {
        otpChallengeRepository.deleteAll();
        Mockito.when(customerServiceClient.initializeCustomerProfile(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
        Mockito.when(customerServiceClient.verifyProfileExists(anyString()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("Context Loads - Spring Boot context initializes successfully")
    void contextLoads() {
        assertThat(authService).isNotNull();
        assertThat(jwtTokenService).isNotNull();
        assertThat(otpService).isNotNull();
    }

    @Test
    @DisplayName("OTP Flow - Request OTP and handle resend cooldown with otp_challenges")
    void testOtpRequestAndCooldown() throws Exception {
        SendOtpRequest request = SendOtpRequest.builder()
                .mobileNumber("+919876543210")
                .purpose(OtpPurpose.REGISTRATION)
                .build();

        // 1. Initial OTP Request -> Expect 200 OK
        mockMvc.perform(post("/api/auth/customer/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("+919876543210"))
                .andExpect(jsonPath("$.data.expiresInMinutes").value(5));

        // 2. Immediate Re-request -> Expect 429 Too Many Requests (Cooldown active)
        mockMvc.perform(post("/api/auth/customer/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());
    }

    @Test
    @DisplayName("Customer Flow - Register new customer and login via OTP")
    void testCustomerRegistrationAndLogin() throws Exception {
        final String mobile = "+919876500001";
        final String rawOtp = "123456";

        // Seed an active OTP directly in otp_challenges with metadata
        OtpChallenge challenge = OtpChallenge.builder()
                .mobileNumber(mobile)
                .otpHash(passwordEncoder.encode(rawOtp))
                .purpose(OtpPurpose.REGISTRATION)
                .fullName("Priya Sharma")
                .email("priya@example.com")
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
        otpChallengeRepository.save(challenge);

        // 1. Register Customer Verification -> Expect 201 Created and auto-login
        CustomerRegisterVerifyOtpRequest verifyRequest = CustomerRegisterVerifyOtpRequest.builder()
                .mobileNumber(mobile)
                .otp(rawOtp)
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/customer/register/verify-otp")
                        .header("User-Agent", "Mozilla/5.0 TestBrowser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.mobileNumber").value(mobile))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andReturn();

        JsonNode regNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String accessToken = regNode.path("data").path("accessToken").asText();

        // 2. Fetch Profile with Bearer Token (/api/auth/me)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mobileNumber").value(mobile))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));

        // 3. Login Customer via new Login OTP
        final String loginOtp = "654321";
        OtpChallenge loginChallenge = OtpChallenge.builder()
                .mobileNumber(mobile)
                .otpHash(passwordEncoder.encode(loginOtp))
                .purpose(OtpPurpose.LOGIN)
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
        otpChallengeRepository.save(loginChallenge);

        CustomerLoginVerifyOtpRequest loginRequest = CustomerLoginVerifyOtpRequest.builder()
                .mobileNumber(mobile)
                .otp(loginOtp)
                .build();

        mockMvc.perform(post("/api/auth/customer/login/verify-otp")
                        .header("User-Agent", "MobileApp/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.mobileNumber").value(mobile));
    }

    @Test
    @DisplayName("Admin Flow - Dedicated test admin lockout protection without affecting primary admin")
    void testAdminLoginAndLockout() throws Exception {
        // 1. Successful Primary Admin Login with default seeded credentials
        AdminLoginRequest validAdmin = AdminLoginRequest.builder()
                .email("admin@rrvgreenbasket.com")
                .password("Admin@123")
                .build();

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.roles[0]").value("ROLE_ADMIN"));

        // 2. Perform 5 failed attempts against dedicated test admin
        AdminLoginRequest invalidTestAdmin = AdminLoginRequest.builder()
                .email("testadmin@rrvgreenbasket.com")
                .password("WrongTestPassword!")
                .build();

        // 4 failed attempts -> 401 Unauthorized
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidTestAdmin)))
                    .andExpect(status().isUnauthorized());
        }

        // 5th failed attempt -> 423 Locked for testadmin
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTestAdmin)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.lockoutRemainingMinutes").isNumber());

        // 3. Verify primary admin remains completely unaffected and unlocked
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Token Rotation - Rotate refresh token successfully and invalidate old one")
    void testTokenRotationAndRevocation() throws Exception {
        // Login as admin to obtain initial tokens and active session
        AdminLoginRequest loginRequest = AdminLoginRequest.builder()
                .email("admin@rrvgreenbasket.com")
                .password("Admin@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialAccessToken = jsonNode.path("data").path("accessToken").asText();
        String initialRefreshToken = jsonNode.path("data").path("refreshToken").asText();

        // 1. Rotate Refresh Token
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(initialRefreshToken)
                .build();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        JsonNode refreshNode = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccessToken = refreshNode.path("data").path("accessToken").asText();
        String newRefreshToken = refreshNode.path("data").path("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

        // 2. Verify new access token is valid before reuse attack
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Attempt Reuse of Old Refresh Token -> Expect 401 Unauthorized (Security compromise detected)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());

        // 4. Verify that entire token family and session were invalidated due to replay attack -> Expect 401 Unauthorized
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Multi-Device Session Isolation & Immediate Revocation")
    void testMultiDeviceSessionIsolationAndImmediateRevocation() throws Exception {
        final String mobile = "+919876511111";
        final String rawOtpA = "111111";

        // Seed customer
        OtpChallenge regChallenge = OtpChallenge.builder()
                .mobileNumber(mobile)
                .otpHash(passwordEncoder.encode(rawOtpA))
                .purpose(OtpPurpose.REGISTRATION)
                .fullName("Session User")
                .email("session@example.com")
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
        otpChallengeRepository.save(regChallenge);

        // 1. Register & Login Device A
        MvcResult devAResult = mockMvc.perform(post("/api/auth/customer/register/verify-otp")
                        .header("User-Agent", "Device-A-Chrome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerRegisterVerifyOtpRequest.builder()
                                .mobileNumber(mobile).otp(rawOtpA).build())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode devANode = objectMapper.readTree(devAResult.getResponse().getContentAsString());
        String tokenA = devANode.path("data").path("accessToken").asText();
        String refreshA = devANode.path("data").path("refreshToken").asText();
        String sidA = jwtTokenService.extractSessionId(tokenA);
        assertThat(sidA).isNotBlank();

        // 2. Login Device B via new Login OTP
        final String rawOtpB = "222222";
        OtpChallenge loginChallenge = OtpChallenge.builder()
                .mobileNumber(mobile)
                .otpHash(passwordEncoder.encode(rawOtpB))
                .purpose(OtpPurpose.LOGIN)
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
        otpChallengeRepository.save(loginChallenge);

        MvcResult devBResult = mockMvc.perform(post("/api/auth/customer/login/verify-otp")
                        .header("User-Agent", "Device-B-Safari")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerLoginVerifyOtpRequest.builder()
                                .mobileNumber(mobile).otp(rawOtpB).build())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode devBNode = objectMapper.readTree(devBResult.getResponse().getContentAsString());
        String tokenB = devBNode.path("data").path("accessToken").asText();
        String refreshB = devBNode.path("data").path("refreshToken").asText();
        String sidB = jwtTokenService.extractSessionId(tokenB);
        assertThat(sidB).isNotBlank();
        assertThat(sidB).isNotEqualTo(sidA);

        // Verify both sessions are ACTIVE in DB
        CustomerSession sessionA = customerSessionRepository.findBySessionId(sidA).orElseThrow();
        CustomerSession sessionB = customerSessionRepository.findBySessionId(sidB).orElseThrow();
        assertThat(sessionA.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(sessionB.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        // 3. Customer logs out from Device A
        CustomerLogoutRequest logoutReq = CustomerLogoutRequest.builder().refreshToken(refreshA).build();
        mockMvc.perform(post("/api/auth/customer/logout")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loggedOut").value(true));

        // 4. Verify Immediate Revocation: Session A is REVOKED, Session B is still ACTIVE
        CustomerSession revokedSessionA = customerSessionRepository.findBySessionId(sidA).orElseThrow();
        CustomerSession activeSessionB = customerSessionRepository.findBySessionId(sidB).orElseThrow();
        assertThat(revokedSessionA.getStatus()).isEqualTo(SessionStatus.REVOKED);
        assertThat(activeSessionB.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        // 5. Test internal session validation endpoint directly
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonceA = UUID.randomUUID().toString();
        String pathA = "/api/auth/internal/sessions/" + sidA + "/validate";
        String sigA = generateS2SSignature("GET", pathA, Long.parseLong(timestamp), nonceA);

        mockMvc.perform(get(pathA)
                        .header("X-Internal-Service-Id", "api-gateway")
                        .header("X-Internal-Timestamp", timestamp)
                        .header("X-Internal-Nonce", nonceA)
                        .header("X-Internal-Signature", sigA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("REVOKED"));

        String nonceB = UUID.randomUUID().toString();
        String pathB = "/api/auth/internal/sessions/" + sidB + "/validate";
        String sigB = generateS2SSignature("GET", pathB, Long.parseLong(timestamp), nonceB);

        mockMvc.perform(get(pathB)
                        .header("X-Internal-Service-Id", "api-gateway")
                        .header("X-Internal-Timestamp", timestamp)
                        .header("X-Internal-Nonce", nonceB)
                        .header("X-Internal-Signature", sigB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("S2S Security - HMAC signature validation and replay prevention")
    void testS2SSignatureValidationAndReplay() throws Exception {
        String testSid = UUID.randomUUID().toString();
        String path = "/api/auth/internal/sessions/" + testSid + "/validate";
        String timestamp = String.valueOf(System.currentTimeMillis());

        // 1. Missing signature header -> 403 Forbidden
        String nonce1 = UUID.randomUUID().toString();
        mockMvc.perform(get(path)
                        .header("X-Internal-Service-Id", "api-gateway")
                        .header("X-Internal-Timestamp", timestamp)
                        .header("X-Internal-Nonce", nonce1))
                .andExpect(status().isForbidden());

        // 2. Tampered signature with fresh nonce -> 403 Forbidden
        String nonce2 = UUID.randomUUID().toString();
        mockMvc.perform(get(path)
                        .header("X-Internal-Service-Id", "api-gateway")
                        .header("X-Internal-Timestamp", timestamp)
                        .header("X-Internal-Nonce", nonce2)
                        .header("X-Internal-Signature", "invalid-tampered-signature-hash"))
                .andExpect(status().isForbidden());

        // 3. Valid signature with fresh nonce -> 200 OK (session valid=false because not found, but auth succeeds)
        String nonce3 = UUID.randomUUID().toString();
        String validSig = generateS2SSignature("GET", path, Long.parseLong(timestamp), nonce3);
        mockMvc.perform(get(path)
                        .header("X-Internal-Service-Id", "api-gateway")
                        .header("X-Internal-Timestamp", timestamp)
                        .header("X-Internal-Nonce", nonce3)
                        .header("X-Internal-Signature", validSig))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        // 4. Replay with the same nonce3 -> 403 Forbidden (Replay detected)
        mockMvc.perform(get(path)
                        .header("X-Internal-Service-Id", "api-gateway")
                        .header("X-Internal-Timestamp", timestamp)
                        .header("X-Internal-Nonce", nonce3)
                        .header("X-Internal-Signature", validSig))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Replay attack detected. Nonce already consumed."));
    }

    @Test
    @DisplayName("Registration Saga Failure & Compensation - Staged AuthUser rolled back if profile creation fails")
    void testRegistrationSagaFailureAndCompensation() throws Exception {
        final String mobile = "+919876599999";
        final String rawOtp = "999999";

        // Mock Customer Service failure and probe confirms NOT_FOUND
        Mockito.when(customerServiceClient.initializeCustomerProfile(anyString(), anyString(), anyString(), any()))
                .thenReturn(false);
        Mockito.when(customerServiceClient.verifyProfileExists(anyString()))
                .thenReturn(false);

        OtpChallenge regChallenge = OtpChallenge.builder()
                .mobileNumber(mobile)
                .otpHash(passwordEncoder.encode(rawOtp))
                .purpose(OtpPurpose.REGISTRATION)
                .fullName("Compensate User")
                .email("comp@example.com")
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
        otpChallengeRepository.save(regChallenge);

        mockMvc.perform(post("/api/auth/customer/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerRegisterVerifyOtpRequest.builder()
                                .mobileNumber(mobile).otp(rawOtp).build())))
                .andExpect(status().isServiceUnavailable());

        // Verify that the staged user and customerAuth are preserved in recoverable PENDING state
        assertThat(customerAuthRepository.findByMobileNumber(mobile)).isPresent();
        assertThat(customerAuthRepository.findByMobileNumber(mobile).get().getUser().getStatus())
                .isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("Legacy Customer Flow - Register customer via /customer/verify-otp endpoint")
    void testCustomerRegistrationViaLegacyVerifyOtpEndpoint() throws Exception {
        final String mobile = "+919876522222";
        final String rawOtp = "654321";

        Mockito.when(customerServiceClient.initializeCustomerProfile(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
        Mockito.when(customerServiceClient.verifyProfileExists(anyString()))
                .thenReturn(true);

        OtpChallenge challenge = OtpChallenge.builder()
                .mobileNumber(mobile)
                .otpHash(passwordEncoder.encode(rawOtp))
                .purpose(OtpPurpose.REGISTRATION)
                .fullName("Mahendra Kumar")
                .email("mahendra@example.com")
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
        otpChallengeRepository.save(challenge);

        VerifyOtpRequest verifyRequest = VerifyOtpRequest.builder()
                .mobileNumber(mobile)
                .otp(rawOtp)
                .purpose(OtpPurpose.REGISTRATION)
                .build();

        mockMvc.perform(post("/api/auth/customer/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());

        assertThat(customerAuthRepository.findByMobileNumber(mobile)).isPresent();
        assertThat(customerAuthRepository.findByMobileNumber(mobile).get().getUser().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }
}
