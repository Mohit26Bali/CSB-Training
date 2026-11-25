package com.company.jwt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.company.jwt.security.JsonSecurityErrorHandlers;
import com.company.jwt.security.JwtAuthenticationFilter;
import com.company.jwt.security.JwtService;
import com.company.jwt.security.RevokedAccessTokens;

/**
 * STATELESS security with JWT:
 *
 *  - no HTTP session (SessionCreationPolicy.STATELESS) and no JSESSIONID cookie;
 *  - no httpBasic and no formLogin: the only way in is a Bearer token, obtained
 *    from POST /api/auth/login;
 *  - CSRF off: the token travels in the Authorization header, which a browser
 *    never adds on its own, so a forged cross-site request carries no token;
 *  - our JwtAuthenticationFilter runs before Spring's login filters.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * The AuthenticationManager that Spring Security builds from our
     * UserDetailsService and PasswordEncoder beans (a DaoAuthenticationProvider).
     * Exposed as a bean so AuthService can call it for POST /api/auth/login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain apiChain(HttpSecurity http, JsonSecurityErrorHandlers errors,
                                        JwtService jwtService, RevokedAccessTokens revokedTokens) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("USER_MANAGE")
                .requestMatchers(HttpMethod.GET, "/api/directory").hasAuthority("EMPLOYEE_READ")
                .anyRequest().authenticated())                     // includes /api/auth/logout and /api/me
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())                    // our own /api/auth/logout revokes tokens
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, revokedTokens, errors),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errors)                  // 401: no token
                .accessDeniedHandler(errors));                     // 403: token fine, authorities not enough
        return http.build();
    }
}
