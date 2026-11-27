package com.springlab.security;

import com.springlab.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a signature-verified JWT into an Authentication whose authorities come from the DATABASE, not from the token.
 *
 * <p>The JWT proves who the caller is (subject) and that we issued it. What that caller may do right now is read
 * from the {@code users} table on every authenticated request, so a role change or a disabled account takes
 * effect on the very next request instead of when the token expires.
 *
 * <p>Cost: one indexed lookup ({@code users.username} is unique) per authenticated request, selecting three columns.
 * Caching this would bring back a staleness window, so it is intentionally not cached.
 * The {@code role} claim stays in the token for clients/diagnostics but is never trusted for authorization.
 */
@Component
public class DatabaseBackedJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public DatabaseBackedJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new InvalidBearerTokenException("Token has no subject");
        }
        // Unknown user and disabled user are deliberately indistinguishable: both are a 401.
        UserAuthInfo user = userRepository.findAuthInfoByUsername(subject)
                .filter(UserAuthInfo::enabled)
                .orElseThrow(() -> new InvalidBearerTokenException("Token is not valid for an active account"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())),
                user.username());
    }
}
