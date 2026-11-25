package com.csb.banking.entity;

/**
 * The two roles from the project description (section 4).
 *
 * <p>Stored on {@link User#getRole()}. {@code service/CustomUserDetailsService} turns it into the Spring Security
 * authority {@code ROLE_CUSTOMER} / {@code ROLE_ADMIN}, which {@code config/SecurityConfig} (URL rules) and the
 * {@code @PreAuthorize} annotations on the services check. The role is also embedded in the JWT by
 * {@code security/JwtService}.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
