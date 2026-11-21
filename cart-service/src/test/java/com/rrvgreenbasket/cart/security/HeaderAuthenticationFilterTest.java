package com.rrvgreenbasket.cart.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeaderAuthenticationFilterTest {

    private static final String TEST_SECRET = "RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==";
    private static final String ISSUER = "rrv-gateway";
    private static final String AUDIENCE = "rrv-internal-services";
    private static final String CUSTOMER_ID = "ab5d2528-78c1-40bc-be23-cc869816db8a";

    private HeaderAuthenticationFilter filter;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        filter = new HeaderAuthenticationFilter(TEST_SECRET, ISSUER, AUDIENCE);
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createAssertion(String customerId, String method, String uri, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(customerId)
                .id(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .claim("sid", "session-123")
                .claim("m", method)
                .claim("p", uri)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    @Test
    @DisplayName("Valid assertion executes filterChain exactly once and sets authentication")
    void validAssertion_executesChainExactlyOnce() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        String token = createAssertion(CUSTOMER_ID, "GET", "/api/cart", 30_000);
        request.addHeader("X-Gateway-Assertion", token);

        filter.doFilter(request, response, filterChain);

        // Verify doFilter is called exactly once (preventing duplicate response JSON)
        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UserPrincipal.class);
    }

    @Test
    @DisplayName("Missing assertion passes through filter chain without authentication")
    void missingAssertion_passesThroughChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Invalid assertion sends 401 and does not execute filter chain")
    void invalidAssertion_sends401AndStopsChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        request.addHeader("X-Gateway-Assertion", "invalid.jwt.token");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
