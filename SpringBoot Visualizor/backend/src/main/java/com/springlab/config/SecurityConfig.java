package com.springlab.config;

import com.springlab.security.DatabaseBackedJwtAuthenticationConverter;
import com.springlab.security.RestAccessDeniedHandler;
import com.springlab.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize / @PostAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DatabaseBackedJwtAuthenticationConverter jwtAuthenticationConverter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF protects cookie/session-based authentication. This API is stateless and authenticates
                // only via an Authorization header that browsers never attach automatically, so it is disabled.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Security also applies to the container's ERROR dispatch; without this a 500 would show as 401.
                        .requestMatchers("/error").permitAll()
                        // Coarse URL-level rule; controllers repeat it with @PreAuthorize (defence in depth).
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Signature/expiry/issuer are checked by the JwtDecoder; authorities come from the database.
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Delegating encoder: stores "{bcrypt}$2a$..." so the algorithm can be upgraded later without a migration. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The manager used by the login endpoint, declared explicitly: a ProviderManager with one DaoAuthenticationProvider
     * (the application's UserDetailsService and PasswordEncoder). Bearer-token requests do not use it.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
