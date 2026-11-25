package com.csb.cinescope.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.csb.cinescope.security.JwtAuthenticationFilter;
import com.csb.cinescope.security.JwtService;
import com.csb.cinescope.security.RestAccessDeniedHandler;
import com.csb.cinescope.security.RestAuthenticationEntryPoint;

/**
 * Who may call what. The rule is "deny by default": only the endpoints listed here are public, everything else needs a valid
 * login token.
 *
 * <ul>
 *   <li><b>Public:</b> register and login; {@code /api/showcase} (the pictures and famous lines on the sign-in screens, which
 *       a visitor sees before having an account); {@code /api/meta}; Swagger.</li>
 *   <li><b>Administrators only:</b> everything under {@code /api/admin/} ({@code hasRole('ADMIN')}: 401 without a login, 403 for an
 *       ordinary user); {@code service/AdminUserService} repeats the check with {@code @PreAuthorize}.</li>
 *   <li><b>Login required:</b> everything else - the catalogue (home, titles, people, discover, search, genres, where to
 *       watch), tonight's pick, {@code /api/auth/me} and the watchlist. Like a streaming service, the app shows nothing but
 *       the sign-in screens until someone signs in; the frontend sends visitors there (auth/RequireAuth) and this class makes
 *       the server enforce the same rule for anyone calling the API directly.</li>
 * </ul>
 * The API is stateless: no session, no CSRF cookie (CSRF protects cookie sessions; this API uses a bearer token).
 *
 * <p>Connections: builds {@link JwtAuthenticationFilter} from {@link JwtService} and {@code service/CustomUserDetailsService};
 * exposes the {@link PasswordEncoder} and {@link AuthenticationManager} used by {@code service/AuthService}. CORS lets the
 * origins in {@code app.cors.allowed-origins} call the API directly.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> { })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/showcase", "/api/meta").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
