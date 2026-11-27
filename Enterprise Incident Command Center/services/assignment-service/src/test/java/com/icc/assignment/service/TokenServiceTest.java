package com.icc.assignment.service;

import com.icc.assignment.entity.AppUser;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.JwtVerifier;
import com.icc.common.jwt.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenServiceTest {

    private static final String SECRET = "assignment-test-secret-assignment-test-12345";

    private final TokenService tokenService = new TokenService(SECRET, 60);
    private final JwtVerifier verifier = new JwtVerifier(SECRET);

    @Test
    void token_canBeVerifiedWithTheSameSecret_andCarriesTheUser() {
        AppUser engineer = new AppUser(2L, "sd.engineer", "hash", "Sam", "s@icc.local",
                Role.SUPPORT_ENGINEER, 1L, true);

        JwtClaims claims = verifier.verify(tokenService.createToken(engineer));

        assertEquals(new JwtClaims(2L, "sd.engineer", Role.SUPPORT_ENGINEER, 1L), claims);
    }

    @Test
    void employeeToken_hasNoTeam() {
        AppUser employee = new AppUser(6L, "employee1", "hash", "Eli", "e@icc.local",
                Role.EMPLOYEE, null, true);

        JwtClaims claims = verifier.verify(tokenService.createToken(employee));

        assertEquals(Role.EMPLOYEE, claims.role());
        assertNull(claims.teamId());
    }

    @Test
    void expiry_isInSeconds() {
        assertEquals(3600, tokenService.expirySeconds());
    }
}
