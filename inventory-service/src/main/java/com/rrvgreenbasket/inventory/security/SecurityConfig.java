package com.rrvgreenbasket.inventory.security;

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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            HeaderAuthenticationFilter headerAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
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

                // 2. Public customer catalog inventory availability
                .requestMatchers(HttpMethod.GET, "/api/inventory/**").permitAll()

                // 3. Internal service-to-service endpoints
                .requestMatchers("/api/internal/inventory/**", "/internal/inventory/**")
                    .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_INTERNAL_SERVICE", "INTERNAL_SERVICE", "ADMIN")

                // 4. Admin Inventory Endpoints strictly require Admin authority
                .requestMatchers("/api/admin/inventory/**")
                    .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ADMIN")

                // 5. Other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
