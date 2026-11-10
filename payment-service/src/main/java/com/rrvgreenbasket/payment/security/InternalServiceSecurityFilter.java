package com.rrvgreenbasket.payment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class InternalServiceSecurityFilter extends OncePerRequestFilter {

    private static final long MAX_SKEW_MS = 30_000L;

    private final S2SSignatureUtil signatureUtil;
    private final NonceReplayCache nonceReplayCache;
    private final ObjectMapper objectMapper;
    private final String internalSecret;
    private final List<String> authorizedServices;

    public InternalServiceSecurityFilter(
            S2SSignatureUtil signatureUtil,
            NonceReplayCache nonceReplayCache,
            ObjectMapper objectMapper,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.security.authorized-internal-services:api-gateway,auth-service,order-service,delivery-service,customer-service}") String authorizedServicesStr) {
        this.signatureUtil = signatureUtil;
        this.nonceReplayCache = nonceReplayCache;
        this.objectMapper = objectMapper;
        this.internalSecret = internalSecret;
        this.authorizedServices = Arrays.stream(authorizedServicesStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/internal/") && request.getHeader("X-Internal-Service-Id") == null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        String serviceId = wrappedRequest.getHeader("X-Internal-Service-Id");
        String timestampHeader = wrappedRequest.getHeader("X-Internal-Timestamp");
        String nonce = wrappedRequest.getHeader("X-Internal-Nonce");
        String signature = wrappedRequest.getHeader("X-Internal-Signature");

        // Allow gateway assertion if present
        String gatewayAssertion = wrappedRequest.getHeader("X-Gateway-Assertion");
        if (gatewayAssertion != null && !gatewayAssertion.isBlank()) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        if (serviceId == null || timestampHeader == null || nonce == null || signature == null) {
            log.warn("Missing S2S headers for internal endpoint: path={}, serviceId={}",
                    wrappedRequest.getRequestURI(), serviceId);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Missing internal service authentication headers");
            return;
        }

        if (!authorizedServices.contains(serviceId.trim().toLowerCase())) {
            log.warn("Unauthorized service caller: '{}' on path: {}", serviceId, wrappedRequest.getRequestURI());
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Unauthorized service caller: " + serviceId);
            return;
        }

        long requestTime;
        try {
            requestTime = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Malformed timestamp header");
            return;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - requestTime) > MAX_SKEW_MS) {
            log.warn("S2S request rejected: timestamp skew exceeded limit (skew={}ms)", Math.abs(now - requestTime));
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Request timestamp expired or invalid clock skew");
            return;
        }

        if (!nonceReplayCache.checkAndStoreNonce(nonce.trim())) {
            log.warn("S2S replay attack detected for nonce '{}' from service '{}'", nonce, serviceId);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Replay attack detected. Nonce already consumed.");
            return;
        }

        byte[] bodyBytes = wrappedRequest.getCachedBody();
        String bodyHash = signatureUtil.calculateBodyHash(bodyBytes);

        String pathAndQuery = wrappedRequest.getRequestURI();
        if (wrappedRequest.getQueryString() != null) {
            pathAndQuery += "?" + wrappedRequest.getQueryString();
        }

        String canonicalString = signatureUtil.buildCanonicalString(
                wrappedRequest.getMethod(),
                pathAndQuery,
                requestTime,
                nonce.trim(),
                bodyHash
        );

        if (!signatureUtil.verifySignature(canonicalString, internalSecret, signature.trim())) {
            log.warn("S2S signature mismatch for service '{}', path '{}'", serviceId, wrappedRequest.getRequestURI());
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Invalid S2S cryptographic signature");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                serviceId,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(wrappedRequest, response);
    }

    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() { return bais.available() == 0; }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setReadListener(ReadListener readListener) {}
                @Override
                public int read() { return bais.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        public byte[] getCachedBody() {
            return cachedBody;
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> apiResponse = ApiResponse.error(message);
        byte[] jsonBytes = objectMapper.writeValueAsString(apiResponse).getBytes(StandardCharsets.UTF_8);
        response.getOutputStream().write(jsonBytes);
    }
}
