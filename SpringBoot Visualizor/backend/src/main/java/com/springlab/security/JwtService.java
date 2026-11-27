package com.springlab.security;

import com.springlab.config.JwtProperties;
import com.springlab.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }

    public AccessToken issueFor(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        // The header must name the algorithm explicitly; NimbusJwtEncoder otherwise defaults to RS256.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, properties.accessTokenTtl().toSeconds());
    }
}
