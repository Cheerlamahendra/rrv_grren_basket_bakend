package com.rrvgreenbasket.payment.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final InternalServiceSecurityFilter internalServiceSecurityFilter;
    private final HeaderAuthenticationFilter headerAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            InternalServiceSecurityFilter internalServiceSecurityFilter,
            HeaderAuthenticationFilter headerAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
        this.internalServiceSecurityFilter = internalServiceSecurityFilter;
        this.headerAuthenticationFilter = headerAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Actuator & OpenAPI documentation
                .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()

                // 2. Razorpay Webhooks (public endpoint secured via HMAC SHA256 signature in controller/service)
                .requestMatchers(
                    "/api/webhooks/**",
                    "/webhooks/**"
                ).permitAll()

                // 3. Internal service-to-service endpoints
                .requestMatchers("/api/internal/payments/**", "/internal/payments/**")
                    .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_INTERNAL_SERVICE", "INTERNAL_SERVICE", "ADMIN")

                // 4. Admin Payment Endpoints strictly require Admin authority
                .requestMatchers("/api/admin/payments/**")
                    .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ADMIN")

                // 5. Customer & other payment endpoints require authentication
                .requestMatchers("/api/payments/**")
                    .authenticated()

                // 6. Default require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalServiceSecurityFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
