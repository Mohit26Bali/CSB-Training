package com.springlab.lab.jwt;

import com.springlab.security.DatabaseBackedJwtAuthenticationConverter;
import com.springlab.security.RestAccessDeniedHandler;
import com.springlab.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A second, narrowly scoped SecurityFilterChain for /api/lab/secure/** only. It is built exactly like the real chain
 * (resource server, stateless, the application's own converter, entry point and access-denied handler) with two
 * differences: the JwtDecoder INSTANCE it uses verifies tokens with the lab key, and the rules protect lab endpoints.
 *
 * <p>Why a second chain: expiry, issuer and wrong-key failures can only be demonstrated for real if the learner can
 * obtain a correctly signed (or deliberately mis-signed) token, which must never involve the application's real signing
 * key. The decoder is passed as an instance to {@code jwt.decoder(...)}, so no second JwtDecoder bean exists and the
 * real chain keeps resolving the single real one.
 *
 * <p>{@code @Order(1)} makes this chain win for its own paths; every other request still falls through to the real chain,
 * whose configuration is untouched.
 */
@Configuration
public class LabSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain labSecureFilterChain(HttpSecurity http, LabJwtService lab,
                                                    DatabaseBackedJwtAuthenticationConverter converter,
                                                    RestAuthenticationEntryPoint authenticationEntryPoint,
                                                    RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityMatcher("/api/lab/secure/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/lab/secure/admin-area").hasRole("ADMIN")
                        .requestMatchers("/api/lab/secure/teacher-area").hasAnyRole("TEACHER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(lab.decoder()).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
