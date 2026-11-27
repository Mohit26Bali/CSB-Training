package com.springlab.lab.jwt;

/**
 * The token faults the simulator can issue. Every variant is signed (or deliberately mis-signed) with a LAB key, never
 * the application's real signing key, so none of them can be used anywhere except the lab endpoints.
 */
public enum LabTokenVariant {
    /** Correctly signed with the lab key, correct issuer, expires in a few minutes. */
    VALID,
    /** Correct signature, but exp is far enough in the past to exceed the decoder's 60 second clock skew. */
    EXPIRED,
    /** Correct signature, but nbf (not before) lies in the future. */
    NOT_YET_VALID,
    /** Signed with a different key than the one the lab decoder verifies with. */
    WRONG_SIGNING_KEY,
    /** Correct signature, but the iss claim names a different issuer. */
    WRONG_ISSUER,
    /** A valid token with one character of the signature altered. */
    TAMPERED_SIGNATURE,
    /** A valid token whose payload was replaced (role claim changed) while the original signature was kept. */
    TAMPERED_PAYLOAD,
    /** Not a JWT in compact form: the signature segment is missing. */
    MALFORMED,
    /** An unsecured JWT: header says alg=none and there is no signature. */
    ALGORITHM_NONE
}
