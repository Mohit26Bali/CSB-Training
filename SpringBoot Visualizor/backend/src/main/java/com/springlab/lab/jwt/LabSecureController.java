package com.springlab.lab.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints protected by the lab filter chain (see LabSecurityConfig). They do nothing except describe the caller, so
 * a lab token can never reach anything of value.
 */
@RestController
@RequestMapping("/api/lab/secure")
public class LabSecureController {

    /** What Spring Security made of the token: authorities come from the database, the claim is only reported. */
    public record WhoAmI(String username, List<String> authoritiesFromDatabase, String tokenRoleClaim,
                         boolean roleClaimMatchesDatabase, String tokenIssuer) {
    }

    public record AreaAccess(String area, String message) {
    }

    @GetMapping("/whoami")
    public WhoAmI whoami(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .sorted().toList();
        String claim = jwt.getClaimAsString(LabJwtService.ROLE_CLAIM);
        return new WhoAmI(authentication.getName(), authorities, claim,
                claim != null && authorities.contains("ROLE_" + claim), jwt.getClaimAsString("iss"));
    }

    @GetMapping("/teacher-area")
    public AreaAccess teacherArea() {
        return new AreaAccess("teacher-area", "Reached: this area requires the TEACHER or ADMIN role in the database.");
    }

    @GetMapping("/admin-area")
    public AreaAccess adminArea() {
        return new AreaAccess("admin-area", "Reached: this area requires the ADMIN role in the database.");
    }
}
