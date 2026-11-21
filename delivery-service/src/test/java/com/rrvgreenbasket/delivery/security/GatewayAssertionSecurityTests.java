package com.rrvgreenbasket.delivery.security;

import com.rrvgreenbasket.delivery.service.StoreLocationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("Gateway Assertion JWT Signing and Verification Tests")
class GatewayAssertionSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StoreLocationService storeLocationService;

    @Value("${app.security.internal-service-secret}")
    private String configuredSecret;

    @Test
    @DisplayName("Valid HS512 Gateway Assertion signed with 512-bit key is successfully verified and authenticates admin")
    void testValidGatewayAssertion_HS512_AuthenticatesSuccessfully() throws Exception {
        byte[] keyBytes = Decoders.BASE64.decode(configuredSecret.trim());
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        String jti = UUID.randomUUID().toString();
        String assertion = Jwts.builder()
                .id(jti)
                .subject("admin-user-id-001")
                .issuer("rrv-gateway")
                .audience().add("rrv-internal-services").and()
                .claim("role", "ROLE_ADMIN")
                .claim("sid", "session-xyz-123")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        mockMvc.perform(get("/api/admin/delivery/settings")
                        .header("X-Gateway-Assertion", assertion))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Customer role in Gateway Assertion is recognized and forbidden on admin endpoint")
    void testCustomerGatewayAssertion_HS512_ReturnsForbidden() throws Exception {
        byte[] keyBytes = Decoders.BASE64.decode(configuredSecret.trim());
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        String jti = UUID.randomUUID().toString();
        String assertion = Jwts.builder()
                .id(jti)
                .subject("customer-user-id-001")
                .issuer("rrv-gateway")
                .audience().add("rrv-internal-services").and()
                .claim("role", "ROLE_CUSTOMER")
                .claim("sid", "session-xyz-456")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        mockMvc.perform(get("/api/admin/delivery/settings")
                        .header("X-Gateway-Assertion", assertion))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Replay of identical Gateway Assertion (same JTI) is detected and rejected")
    void testReplayGatewayAssertion_ReturnsUnauthorized() throws Exception {
        byte[] keyBytes = Decoders.BASE64.decode(configuredSecret.trim());
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        String jti = UUID.randomUUID().toString();
        String assertion = Jwts.builder()
                .id(jti)
                .subject("admin-user-id-002")
                .issuer("rrv-gateway")
                .audience().add("rrv-internal-services").and()
                .claim("role", "ROLE_ADMIN")
                .claim("sid", "session-replay-1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        // First call should succeed
        mockMvc.perform(get("/api/admin/delivery/settings")
                        .header("X-Gateway-Assertion", assertion))
                .andExpect(status().isOk());

        // Replay with identical assertion should be rejected as replay
        mockMvc.perform(get("/api/admin/delivery/settings")
                        .header("X-Gateway-Assertion", assertion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Gateway Assertion signed with different secret key is rejected with 401")
    void testTamperedGatewayAssertion_ReturnsUnauthorized() throws Exception {
        // Different 512-bit key
        SecretKey differentKey = Keys.hmacShaKeyFor(new byte[64]);

        String jti = UUID.randomUUID().toString();
        String assertion = Jwts.builder()
                .id(jti)
                .subject("admin-user-id-003")
                .issuer("rrv-gateway")
                .audience().add("rrv-internal-services").and()
                .claim("role", "ROLE_ADMIN")
                .claim("sid", "session-tamper-1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(differentKey, Jwts.SIG.HS512)
                .compact();

        mockMvc.perform(get("/api/admin/delivery/settings")
                        .header("X-Gateway-Assertion", assertion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HeaderAuthenticationFilter constructor fails fast on secret less than 512 bits")
    void testHeaderAuthenticationFilter_FailsFastOnShortSecret() {
        // 256-bit Base64 string (32 bytes = 256 bits)
        String shortBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        assertThrows(IllegalStateException.class, () ->
                new HeaderAuthenticationFilter(null, null, shortBase64, "rrv-gateway", "rrv-internal-services")
        );
    }
}
