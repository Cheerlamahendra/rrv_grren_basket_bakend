package com.rrvgreenbasket.customer.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig
 *
 * <p><strong>What it does:</strong></p>
 * Configures the Spring Security filter chain for Customer Service.
 * Enforces stateless session management, internal S2S authentication for internal endpoints,
 * and standard exception handling envelopes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;
    private final InternalServiceSecurityFilter internalServiceSecurityFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            HeaderAuthenticationFilter headerAuthenticationFilter,
            InternalServiceSecurityFilter internalServiceSecurityFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
        this.headerAuthenticationFilter = headerAuthenticationFilter;
        this.internalServiceSecurityFilter = internalServiceSecurityFilter;
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
                // 1. Internal S2S endpoints (Probes and Saga Compensation)
                .requestMatchers("/api/customers/internal/**").hasAuthority("ROLE_INTERNAL_SERVICE")

                // 2. Monitoring & Documentation
                .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()

                // 3. Customer and Admin operations require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalServiceSecurityFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
