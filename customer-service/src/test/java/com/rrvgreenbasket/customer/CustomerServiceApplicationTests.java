package com.rrvgreenbasket.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.customer.client.AuthServiceClient;
import com.rrvgreenbasket.customer.dto.request.AddressRequest;
import com.rrvgreenbasket.customer.dto.request.CreateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.request.UpdateCustomerProfileRequest;
import com.rrvgreenbasket.customer.enums.AddressType;
import com.rrvgreenbasket.customer.repository.CustomerAddressRepository;
import com.rrvgreenbasket.customer.repository.CustomerRepository;
import com.rrvgreenbasket.customer.security.S2SSignatureUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CustomerServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private S2SSignatureUtil signatureUtil;

    @MockBean
    private AuthServiceClient authServiceClient;

    @Value("${app.security.internal-service-secret}")
    private String internalServiceSecret;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private final String testAuthUserId = UUID.randomUUID().toString();
    private final String testEmail = "rajesh.kumar@example.com";
    private final String testMobile = "9876543210";
    private final String testSessionId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        customerRepository.deleteAll();

        // Default mock: session is active
        Mockito.when(authServiceClient.isSessionActive(anyString())).thenReturn(true);
    }

    private String createGatewayAssertion(String userId, String email, String mobile, String role, String sessionId) {
        long now = System.currentTimeMillis();
        byte[] keyBytes = internalServiceSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("mobile_number", mobile)
                .claim("role", role)
                .claim("sid", sessionId)
                .claim("status", "ACTIVE")
                .claim("nonce", UUID.randomUUID().toString())
                .issuer("rrv-green-basket-gateway")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 30_000))
                .signWith(key)
                .compact();
    }

    private String createBearerJwt(String userId, String email, String mobile, String role, String sessionId) {
        long now = System.currentTimeMillis();
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("public_id", userId)
                .claim("email", email)
                .claim("mobile_number", mobile)
                .claim("role", role)
                .claim("roles", List.of(role))
                .claim("sid", sessionId)
                .claim("status", "ACTIVE")
                .issuer("rrv-green-basket-auth")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 1_800_000))
                .signWith(key)
                .compact();
    }

    private String generateS2SSignature(String method, String path, byte[] bodyBytes, long timestamp, String nonce) {
        String bodyHash = signatureUtil.calculateBodyHash(bodyBytes != null ? bodyBytes : new byte[0]);
        String canonical = signatureUtil.buildCanonicalString(method, path, timestamp, nonce, bodyHash);
        return signatureUtil.calculateSignature(canonical, internalServiceSecret);
    }

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        assertThat(customerRepository).isNotNull();
        assertThat(addressRepository).isNotNull();
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void unauthenticatedRequest_Returns401() throws Exception {
        mockMvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Header Spoofing Prevention - Raw X-User-* headers without signed assertion return 401")
    void spoofedHeaders_Rejected401() throws Exception {
        mockMvc.perform(get("/api/customers/me")
                        .header("X-User-Id", testAuthUserId)
                        .header("X-User-Role", "ROLE_CUSTOMER")
                        .header("X-Session-Id", testSessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("S2S Profile Creation, Idempotency, Probe & Compensation Lifecycle")
    void s2sProfileLifecycle_Success() throws Exception {
        String path = "/api/customers";
        CreateCustomerProfileRequest createRequest = CreateCustomerProfileRequest.builder()
                .authUserId(testAuthUserId)
                .fullName("Rajesh Kumar")
                .mobileNumber(testMobile)
                .email(testEmail)
                .profileImageUrl("https://cdn.rrvgreenbasket.com/profiles/rajesh.jpg")
                .build();
        byte[] payloadBytes = objectMapper.writeValueAsBytes(createRequest);

        long timestamp = System.currentTimeMillis();
        String nonce1 = UUID.randomUUID().toString();
        String sig1 = generateS2SSignature("POST", path, payloadBytes, timestamp, nonce1);

        // 1. Initial S2S Profile Creation -> 201 Created
        MvcResult createResult = mockMvc.perform(post(path)
                        .header("Idempotency-Key", testAuthUserId)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce1)
                        .header("X-Internal-Signature", sig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Rajesh Kumar"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        // 2. Idempotent Retry with same AuthUserId -> 201 Created returning existing profile
        String nonce2 = UUID.randomUUID().toString();
        String sig2 = generateS2SSignature("POST", path, payloadBytes, timestamp, nonce2);

        mockMvc.perform(post(path)
                        .header("Idempotency-Key", testAuthUserId)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce2)
                        .header("X-Internal-Signature", sig2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Rajesh Kumar"));

        // 3. S2S State Probe: GET /api/customers/internal/by-auth-user/{authUserId} -> 200 OK
        String probePath = "/api/customers/internal/by-auth-user/" + testAuthUserId;
        String nonce3 = UUID.randomUUID().toString();
        String sig3 = generateS2SSignature("GET", probePath, null, timestamp, nonce3);

        mockMvc.perform(get(probePath)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce3)
                        .header("X-Internal-Signature", sig3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Rajesh Kumar"));

        // 4. S2S Saga Compensation: DELETE /api/customers/internal/by-auth-user/{authUserId} -> 204 No Content
        String nonce4 = UUID.randomUUID().toString();
        String sig4 = generateS2SSignature("DELETE", probePath, null, timestamp, nonce4);

        mockMvc.perform(delete(probePath)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce4)
                        .header("X-Internal-Signature", sig4))
                .andExpect(status().isOk());

        // 5. Verify probe after compensation -> 404 Not Found
        String nonce5 = UUID.randomUUID().toString();
        String sig5 = generateS2SSignature("GET", probePath, null, timestamp, nonce5);

        mockMvc.perform(get(probePath)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce5)
                        .header("X-Internal-Signature", sig5))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Gateway Assertion - Customer /me and address lifecycle with ROLE_CUSTOMER")
    void gatewayAssertionCustomerLifecycle_Success() throws Exception {
        // 1. Seed customer profile via S2S
        String path = "/api/customers";
        CreateCustomerProfileRequest createRequest = CreateCustomerProfileRequest.builder()
                .authUserId(testAuthUserId)
                .fullName("Rajesh Kumar")
                .mobileNumber(testMobile)
                .email(testEmail)
                .build();
        byte[] payloadBytes = objectMapper.writeValueAsBytes(createRequest);
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String sig = generateS2SSignature("POST", path, payloadBytes, timestamp, nonce);

        mockMvc.perform(post(path)
                        .header("Idempotency-Key", testAuthUserId)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce)
                        .header("X-Internal-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isCreated());

        // 2. Fetch /me using X-Gateway-Assertion with ROLE_CUSTOMER
        String gatewayAssertion = createGatewayAssertion(testAuthUserId, testEmail, testMobile, "ROLE_CUSTOMER", testSessionId);

        mockMvc.perform(get("/api/customers/me")
                        .header("X-Gateway-Assertion", gatewayAssertion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Rajesh Kumar"));

        // 3. Update Profile
        UpdateCustomerProfileRequest updateRequest = UpdateCustomerProfileRequest.builder()
                .fullName("Rajesh V. Kumar")
                .email("rajesh.vk@example.com")
                .build();

        mockMvc.perform(put("/api/customers/me")
                        .header("X-Gateway-Assertion", createGatewayAssertion(testAuthUserId, testEmail, testMobile, "ROLE_CUSTOMER", testSessionId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Rajesh V. Kumar"));

        // 4. Add Address
        AddressRequest address1 = AddressRequest.builder()
                .receiverName("Rajesh Kumar")
                .receiverMobile(testMobile)
                .addressLine1("Flat 101, Green Meadows")
                .city("Bangalore")
                .state("Karnataka")
                .pincode("560038")
                .addressType(AddressType.HOME)
                .build();

        mockMvc.perform(post("/api/customers/me/addresses")
                        .header("X-Gateway-Assertion", createGatewayAssertion(testAuthUserId, testEmail, testMobile, "ROLE_CUSTOMER", testSessionId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(address1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.city").value("Bangalore"));

        // 5. Verify ROLE_ADMIN calling /api/customers/me is FORBIDDEN (403)
        String adminAssertion = createGatewayAssertion(testAuthUserId, "admin@rrvgreenbasket.com", null, "ROLE_ADMIN", testSessionId);

        mockMvc.perform(get("/api/customers/me")
                        .header("X-Gateway-Assertion", adminAssertion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Direct Bearer JWT - Active session succeeds, revoked session is rejected with 401")
    void directBearerJwtAndRevokedSessionTest() throws Exception {
        // 1. Seed customer profile
        String path = "/api/customers";
        CreateCustomerProfileRequest createRequest = CreateCustomerProfileRequest.builder()
                .authUserId(testAuthUserId)
                .fullName("Bearer User")
                .mobileNumber("+919876599901")
                .email("bearer@example.com")
                .build();
        byte[] payloadBytes = objectMapper.writeValueAsBytes(createRequest);
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String sig = generateS2SSignature("POST", path, payloadBytes, timestamp, nonce);

        mockMvc.perform(post(path)
                        .header("Idempotency-Key", testAuthUserId)
                        .header("X-Internal-Service-Id", "auth-service")
                        .header("X-Internal-Timestamp", String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce)
                        .header("X-Internal-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isCreated());

        String jwt = createBearerJwt(testAuthUserId, "bearer@example.com", "+919876599901", "ROLE_CUSTOMER", testSessionId);

        // 2. Active Session -> 200 OK
        Mockito.when(authServiceClient.isSessionActive(testSessionId)).thenReturn(true);

        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Revoked Session -> 401 Unauthorized
        Mockito.when(authServiceClient.isSessionActive(testSessionId)).thenReturn(false);

        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
