package com.csb.cinescope.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;

/**
 * Spring Security's user record plus one extra fact: the account's current {@code tokenVersion}. Built by
 * {@code service/CustomUserDetailsService} for each request; {@link JwtAuthenticationFilter} compares the version with the
 * one inside the presented token, so a token issued before the password changed, the role changed or the account was
 * disabled is refused even though its signature and expiry are fine.
 */
public class CineScopeUserDetails extends org.springframework.security.core.userdetails.User {

    private final int tokenVersion;

    public CineScopeUserDetails(String username, String password, boolean enabled, Collection<? extends GrantedAuthority> authorities,
                                int tokenVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
