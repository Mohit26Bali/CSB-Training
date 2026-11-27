package com.springlab.lab.jwt;

import com.springlab.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request / response types of the JWT simulator API (kept together because each is tiny). */
public final class LabJwtDtos {

    private LabJwtDtos() {
    }

    /** The subject is never accepted from the client: a lab token always describes the caller. */
    public record IssueTokenRequest(@NotNull LabTokenVariant variant, Role claimedRole) {
    }

    public record InspectTokenRequest(@NotBlank @Size(max = 4096) String token) {
        @Override
        public String toString() {
            return "InspectTokenRequest[token=***]"; // never print a pasted token
        }
    }

    /**
     * A token signed with the LAB key. It is not a credential for SpringLab: the real JwtDecoder rejects it, and it is
     * accepted only at /api/lab/secure/** and by the inspector.
     */
    public record IssuedLabToken(LabTokenVariant variant, String token, String actualRole, String claimedRole,
                                 String usableAt, TokenInspection inspection) {
        @Override
        public String toString() {
            return "IssuedLabToken[variant=" + variant + ", token=***]";
        }
    }
}
