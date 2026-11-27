package com.icc.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.common.error.ErrorCode;
import com.icc.common.feign.FeignForwardingConfig;
import com.icc.common.jwt.JwtVerifier;
import com.icc.common.security.ErrorResponseWriter;
import com.icc.common.security.JwtAuthenticationFilter;
import com.icc.common.web.CorrelationIdFilter;
import com.icc.common.web.GlobalExceptionHandler;

@Configuration
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, FeignForwardingConfig.class})
public class CommonConfig {

    @Bean
    public JwtVerifier jwtVerifier(@Value("${JWT_SECRET}") String secret) {
        return new JwtVerifier(secret);
    }

    @Bean
    public ErrorResponseWriter errorResponseWriter(ObjectMapper objectMapper) {
        return new ErrorResponseWriter(objectMapper);
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtVerifier jwtVerifier,
            ErrorResponseWriter errorWriter,
            @Value("${icc.security.public-paths:/actuator/health}") String[] publicPaths) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                errorWriter.write(request, response, ErrorCode.UNAUTHORIZED,
                                        "Authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                errorWriter.write(request, response, ErrorCode.FORBIDDEN,
                                        "You are not allowed to do this")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier, errorWriter),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
