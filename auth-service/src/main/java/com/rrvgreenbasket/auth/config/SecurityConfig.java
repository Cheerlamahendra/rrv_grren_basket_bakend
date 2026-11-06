package com.rrvgreenbasket.auth.config;

import com.rrvgreenbasket.auth.security.CustomAccessDeniedHandler;
import com.rrvgreenbasket.auth.security.InternalServiceSecurityFilter;
import com.rrvgreenbasket.auth.security.JwtAuthenticationEntryPoint;
import com.rrvgreenbasket.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig
 *
 * <p><strong>What it does:</strong></p>
 * Configures Spring Security filter chain for the Authentication microservice.
 * Enforces authenticated S2S communication for internal endpoints, public onboarding routes,
 * and Bearer token verification for customer logout and profile inspection.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalServiceSecurityFilter internalServiceSecurityFilter;

    public SecurityConfig(
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalServiceSecurityFilter internalServiceSecurityFilter) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalServiceSecurityFilter = internalServiceSecurityFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. Internal Microservice Communication (Protected by S2S Security Filter)
                        .requestMatchers("/api/auth/internal/**").hasAuthority("ROLE_INTERNAL_SERVICE")

                        // 2. Public Authentication Endpoints (Registration & Login)
                        .requestMatchers(
                                "/api/auth/customer/register/**",
                                "/api/auth/customer/login/**",
                                "/api/auth/customer/refresh",
                                "/api/auth/customer/send-otp",
                                "/api/auth/customer/verify-otp",
                                "/api/auth/customer/register",
                                "/api/auth/customer/login",
                                "/api/auth/admin/login",
                                "/api/auth/refresh"
                        ).permitAll()

                        // 3. Monitoring & Health Probes
                        .requestMatchers("/actuator/**").permitAll()

                        // 4. Swagger UI & OpenAPI Specification
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // 5. Customer Logout & Profile Inspection (Bearer Token Required)
                        .requestMatchers(
                                "/api/auth/customer/logout",
                                "/api/auth/logout",
                                "/api/auth/me"
                        ).authenticated()

                        // 6. All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalServiceSecurityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
