package com.springlab.lab.jwt;

import java.util.List;
import java.util.Map;

/**
 * The result of looking at a lab token in two clearly separate ways:
 * <ul>
 *   <li><b>Decoding</b>: Base64URL-decoding the header and payload. Needs no key and proves nothing.</li>
 *   <li><b>Verification</b>: the lab decoder checking signature and claims with the lab key.</li>
 * </ul>
 * {@code stages} is a reconstruction of the checks one at a time with the same framework components; the decoder itself
 * only reports its first failure. A test asserts that the reconstruction always agrees with the decoder.
 */
public record TokenInspection(
        int length,
        int parts,
        Map<String, Object> header,
        Map<String, Object> claims,
        String signaturePreview,
        String decodeError,
        Verification verification,
        List<Stage> stages,
        boolean stagesAgreeWithDecoder) {

    /** The actual lab JwtDecoder's verdict. provenance is always CAPTURED: it is the real decoder's own answer. */
    public record Verification(boolean valid, String category, String frameworkMessage, String provenance) {
    }

    /** status is PASSED, FAILED or NOT_REACHED. provenance is always DERIVED (reconstructed). */
    public record Stage(String id, String label, String status, String detail, String provenance) {
    }
}
