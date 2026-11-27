package com.springlab.lab.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The two helpers that decide what of a request header may ever be stored in a trace. */
class TraceFilterHelpersTest {

    @Test
    void onlyTheSchemeWordOfTheAuthorizationHeaderIsKept() {
        assertThat(LifecycleTraceFilter.schemeOf("Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig")).isEqualTo("Bearer");
        assertThat(LifecycleTraceFilter.schemeOf("Basic dXNlcjpwYXNz")).isEqualTo("Basic");
        assertThat(LifecycleTraceFilter.schemeOf("bearer abc")).isEqualTo("bearer");
        assertThat(LifecycleTraceFilter.schemeOf(null)).isNull();
        assertThat(LifecycleTraceFilter.schemeOf("  ")).isNull();
    }

    @Test
    void aSchemeWordThatLooksLikeASecretIsNotStored() {
        assertThat(LifecycleTraceFilter.schemeOf("eyJhbGciOiJIUzI1NiJ9.e30.SomeVeryLongTokenWithoutAScheme"))
                .isEqualTo("(unrecognized)");
    }

    @Test
    void theServersOwnChallengeIsKeptButShortened() {
        String challenge = "Bearer error=\"invalid_token\", error_description=\"Jwt expired at 2026-01-01T00:00:00Z\"";

        assertThat(LifecycleTraceFilter.safeChallenge(challenge, "Bearer sometoken")).isEqualTo(challenge);
        assertThat(LifecycleTraceFilter.safeChallenge("x".repeat(1000), null)).hasSize(401).endsWith("…");
        assertThat(LifecycleTraceFilter.safeChallenge(null, "Bearer t")).isNull();
    }

    @Test
    void aChallengeThatEchoesTheCredentialsIsDroppedEntirely() {
        String leaked = "Bearer error=\"invalid_token\", error_description=\"bad token abc.def.ghi\"";

        assertThat(LifecycleTraceFilter.safeChallenge(leaked, "Bearer abc.def.ghi"))
                .doesNotContain("abc.def.ghi").contains("redacted");
    }
}
