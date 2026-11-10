package com.rrvgreenbasket.review.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;
    private final InternalServiceSecurityFilter internalServiceSecurityFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public Swagger & Actuator
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()

                        // Public product review read & summary APIs
                        .requestMatchers(HttpMethod.GET, "/api/reviews/products/{productPublicId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/products/{productPublicId}/summary").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reviews/products/summaries").permitAll()

                        // Internal microservice endpoints
                        .requestMatchers("/api/internal/**").hasAnyAuthority("ROLE_INTERNAL_SERVICE", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                        // Admin review moderation endpoints
                        .requestMatchers("/api/admin/reviews/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ADMIN")

                        // Customer authenticated review endpoints
                        .requestMatchers("/api/reviews/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalServiceSecurityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(headerAuthenticationFilter, InternalServiceSecurityFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-User-Id", "X-User-Role", "X-User-Roles", "X-User-Email", "X-Gateway-Assertion", "X-Internal-Service", "X-Internal-Service-Id", "X-Internal-Timestamp", "X-Internal-Nonce", "X-Internal-Signature"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
