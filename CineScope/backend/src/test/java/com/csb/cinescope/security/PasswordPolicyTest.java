package com.csb.cinescope.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests of {@link PasswordPolicy}: what is accepted, each way a password can be too weak, and the look-alike tricks. */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = { "Sunset-Rain-42!", "t0Mato&Basil", "Zebra#Quartz9", "Correct Horse 7 Bat!", "Ünïcode-Ärger-9x", "aA1!aA1!" })
    void strongPasswordsAreAccepted(String password) {
        assertNull(PasswordPolicy.problem(password), password);
    }

    @Test
    void eachMissingKindOfCharacterIsNamed() {
        assertEquals("password must include an uppercase letter, a digit and a symbol such as ! @ # or -", PasswordPolicy.problem("lowercaseonly"));
        assertEquals("password must include a lowercase letter, a digit and a symbol such as ! @ # or -", PasswordPolicy.problem("UPPERCASEONLY"));
        assertEquals("password must include a lowercase letter, an uppercase letter and a symbol such as ! @ # or -", PasswordPolicy.problem("12345678901"));
        assertEquals("password must include a symbol such as ! @ # or -", PasswordPolicy.problem("Abcdefg123"));
        assertEquals("password must include a digit", PasswordPolicy.problem("Abcdefgh-ijk"));
        assertEquals("password must include an uppercase letter", PasswordPolicy.problem("abcdefg-123"));
        assertEquals("password must include a lowercase letter", PasswordPolicy.problem("ABCDEFG-123"));
    }

    @Test
    void aSpaceIsNotASymbol() {
        assertEquals("password must include a symbol such as ! @ # or -", PasswordPolicy.problem("Abcdef 12345"));
    }

    @Test
    void theSameCharacterMoreThanThreeTimesInARowIsRejected() {
        assertEquals("password must not repeat the same character more than 3 times in a row", PasswordPolicy.problem("Gooood-Day-1"));
        assertNull(PasswordPolicy.problem("Goood-Day-1x"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Password1!", "P@ssw0rd!", "Password-1", "Qwerty123!", "Welcome@2024", "Admin#12345", "Letmein-99", "Iloveyou1!", "Crestline-1", "CineScope#9" })
    void commonPasswordsAreRejectedEvenWithDecorations(String password) {
        assertEquals("password is too common; choose something harder to guess", PasswordPolicy.problem(password), password);
    }

    @Test
    void lengthIsLeftToSizeAndNullIsLeftToNotBlank() {
        assertNull(PasswordPolicy.problem(null));
        assertNull(PasswordPolicy.problem(""));
        assertNull(PasswordPolicy.problem("Ab1!"));
        assertNull(PasswordPolicy.problem("A".repeat(21)));
    }

    @Test
    void thePasswordMustNotContainTheUsernameOrTheEmailName() {
        assertEquals("password must not contain your username", PasswordPolicy.identityProblem("Asha.Rao-2024!", "asha.rao", "x@example.com"));
        assertEquals("password must not contain your username", PasswordPolicy.identityProblem("MyRAVI-pass1!", "Ravi", null));
        assertEquals("password must not contain your email name", PasswordPolicy.identityProblem("Meera.k#2024", "someone", "meera.k@example.com"));
        assertNull(PasswordPolicy.identityProblem("Sunset-Rain-42!", "asha", "asha@example.com"));
    }

    @Test
    void veryShortNamesAreNotMatchedInsideAPassword() {
        assertNull(PasswordPolicy.identityProblem("Sunset-Rain-42!", "ab", "al@example.com"));
        assertNull(PasswordPolicy.identityProblem("Sunset-Rain-42!", null, null));
    }

    @Test
    void theLookAlikeUndoingOnlyLooksAtLetters() {
        assertTrue(PasswordPolicy.isCommon("p@$$w0rd"));
        assertNotNull(PasswordPolicy.problem("P@$$w0rd"));
        assertTrue(!PasswordPolicy.isCommon("Zebra#Quartz9"));
    }
}
