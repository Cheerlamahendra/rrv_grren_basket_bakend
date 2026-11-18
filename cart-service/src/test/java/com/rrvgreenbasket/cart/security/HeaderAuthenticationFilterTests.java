package com.rrvgreenbasket.cart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.cart.client.AuthServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeaderAuthenticationFilter Security Tests")
class HeaderAuthenticationFilterTests {

    private static final String SECRET = "c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0";
    private static final String ISSUER = "rrv-gateway";
    private static final String AUDIENCE = "rrv-internal-services";

    @Mock
    private ReplayProtectionService replayProtectionService;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private FilterChain filterChain;

    private HeaderAuthenticationFilter filter;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        filter = new HeaderAuthenticationFilter(
                replayProtectionService,
                authServiceClient,
                objectMapper,
                SECRET,
                ISSUER,
                AUDIENCE
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String buildAssertion(String userId, String role, String sessionId, String method, String path, long expiryMs, String customIssuer, String customAudience, SecretKey key) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .issuer(customIssuer != null ? customIssuer : ISSUER)
                .audience().add(customAudience != null ? customAudience : AUDIENCE).and()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("role", role)
                .claim("sid", sessionId)
                .claim("m", method)
                .claim("p", path)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key != null ? key : signingKey)
                .compact();
    }

    @Test
    @DisplayName("Valid gateway assertion successfully authenticates and invokes filterChain exactly once")
    void testValidAssertion_Success() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(replayProtectionService.checkAndStoreNonce(anyString(), anyLong())).thenReturn(true);
        when(authServiceClient.isSessionActive("sess-456")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        // Verify filterChain called exactly once
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("cust-123", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Missing assertion proceeds unauthenticated to Spring Security without error")
    void testMissingAssertion_ProceedsUnauthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Invalid signature rejects with 401 and does NOT invoke filterChain")
    void testInvalidSignature_Rejects401() throws ServletException, IOException {
        SecretKey wrongKey = Keys.hmacShaKeyFor("0000000000000000000000000000000000000000000000000000000000000000".getBytes(StandardCharsets.UTF_8));
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart", 30_000L, null, null, wrongKey);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Expired assertion rejects with 401 and does NOT invoke filterChain")
    void testExpiredAssertion_Rejects401() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart", -5000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Wrong issuer rejects with 401")
    void testWrongIssuer_Rejects401() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart", 30_000L, "wrong-issuer", null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Wrong audience rejects with 401")
    void testWrongAudience_Rejects401() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart", 30_000L, null, "wrong-audience", null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Wrong HTTP method binding rejects with 401")
    void testMethodMismatch_Rejects401() throws ServletException, IOException {
        // Assertion was issued for POST, but request is GET
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "POST", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Wrong path binding rejects with 401")
    void testPathMismatch_Rejects401() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart/items", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Non-customer role rejects with 403 Forbidden")
    void testNonCustomerRole_Rejects403() throws ServletException, IOException {
        String token = buildAssertion("admin-123", "ADMIN", "sess-456", "GET", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Replayed assertion nonce rejects with 401")
    void testReplayedAssertion_Rejects401() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "sess-456", "GET", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(replayProtectionService.checkAndStoreNonce(anyString(), anyLong())).thenReturn(false); // Replay!

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Revoked session rejects with 401")
    void testRevokedSession_Rejects401() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "revoked-sess", "GET", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(replayProtectionService.checkAndStoreNonce(anyString(), anyLong())).thenReturn(true);
        when(authServiceClient.isSessionActive("revoked-sess")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Synthetic order-saga S2S session bypasses Auth Service revocation check and succeeds")
    void testSyntheticOrderSagaSession_BypassesAuthServiceCheck_Success() throws ServletException, IOException {
        String token = buildAssertion("cust-123", "CUSTOMER", "order-saga-04347f29-0f70-4f98-9131-3963db9adc28", "GET", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(replayProtectionService.checkAndStoreNonce(anyString(), anyLong())).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(authServiceClient, never()).isSessionActive(anyString());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("cust-123", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Legacy USER role is accepted as a valid customer role")
    void testLegacyUserRole_Success() throws ServletException, IOException {
        String token = buildAssertion("user-123", "USER", "sess-456", "GET", "/api/cart", 30_000L, null, null, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Gateway-Assertion", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(replayProtectionService.checkAndStoreNonce(anyString(), anyLong())).thenReturn(true);
        when(authServiceClient.isSessionActive("sess-456")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user-123", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus());
    }
}
