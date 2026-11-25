package com.csb.cinescope.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.csb.cinescope.AbstractApiTest;

/**
 * Accounts through the real stack: registration (what is stored, validation, duplicates), login, and who-am-I with and
 * without a valid token. The database is in-memory H2 (profile "test"); no TMDB is involved.
 */
class AuthApiTest extends AbstractApiTest {

    @Override
    protected boolean signedInByDefault() {
        return false; // several tests count the users, and /me is tested without a token
    }

    private static final String VALID = """
            {"username":"%s","email":"%s","password":"%s","displayName":"%s"}""";

    private String body(String username, String email, String password, String displayName) {
        return VALID.formatted(username, email, password, displayName);
    }

    @Test
    void registrationCreatesAnAccountAndLogsTheUserIn() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Asha", "Asha@Example.com", "Secret-Pass-1!", "Asha Rao")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("asha"))
                .andExpect(jsonPath("$.displayName").value("Asha Rao"))
                .andExpect(jsonPath("$.region").value("IN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void thePasswordIsStoredOnlyAsABcryptHash() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("asha", "asha@example.com", "Secret-Pass-1!", "Asha")));

        String stored = userRepository.findByUsername("asha").orElseThrow().getPassword();
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash, got: " + stored.substring(0, 4));
        assertEquals(false, stored.contains("Secret-Pass-1!"));
    }

    @Test
    void invalidRegistrationsAreRejectedWithAMessagePerField() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "not-an-email", "short", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").value("password must be 8 to 20 characters"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("bad name!", "a@example.com", "Secret-Pass-1!", "A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        assertEquals(0, userRepository.count());
    }

    @Test
    void aTakenUsernameOrEmailIsAConflict_ignoringCase() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("asha", "asha@example.com", "Secret-Pass-1!", "Asha")));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("ASHA", "other@example.com", "Secret-Pass-1!", "Other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username 'asha' is already taken"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("other", "ASHA@example.com", "Secret-Pass-1!", "Other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
        assertEquals(1, userRepository.count());
    }

    @Test
    void weakPasswordsAreRejectedWithAMessageSayingWhatIsMissing() throws Exception {
        String[][] cases = {
                { "alllowercase", "password must include an uppercase letter, a digit and a symbol such as ! @ # or -" },
                { "NoSymbolHere1", "password must include a symbol such as ! @ # or -" },
                { "Gooood-Day-1", "password must not repeat the same character more than 3 times in a row" },
                { "Password1!", "password is too common; choose something harder to guess" },
                { "P@ssw0rd", "password is too common; choose something harder to guess" },
        };
        for (String[] c : cases) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body("asha", "asha@example.com", c[0], "Asha")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").value(c[1]));
        }
        assertEquals(0, userRepository.count());
    }

    @Test
    void aPasswordLongerThan20CharactersIsRejectedAndOneOfExactly20IsAccepted() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("asha", "asha@example.com", "Sunset-Rain-42!abcdef", "Asha"))) // 21 characters
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("password must be 8 to 20 characters"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("asha", "asha@example.com", "Sunset-Rain-42!abcde", "Asha"))) // exactly 20
                .andExpect(status().isCreated());
    }

    @Test
    void aPasswordContainingTheUsernameOrEmailNameIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("meera.k", "someone@example.com", "My-meera.k-pass9!", "Meera")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not contain your username"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("someone", "Ravikumar@example.com", "Mine-RAVIKUMAR-9!", "Ravi")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not contain your email name"));
        assertEquals(0, userRepository.count());
    }

    @Test
    void anOldAccountWithAWeakPasswordCanStillSignIn() throws Exception {
        // created before the rules existed (stored directly, as an earlier version of the app would have)
        userRepository.save(new com.csb.cinescope.entity.User("veteran", "veteran@example.com",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("weakpass1"), "Old Timer", "IN"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"veteran\",\"password\":\"weakpass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWorksWithTheRegisteredPasswordAndGivesTheSameMessageForEveryFailure() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("asha", "asha@example.com", "Secret-Pass-1!", "Asha Rao")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"password\":\"Secret-Pass-1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.displayName").value("Asha Rao"));

        // wrong password and unknown user: identical answers, so nobody can find out which usernames exist
        for (String json : new String[] { "{\"username\":\"asha\",\"password\":\"wrong-password\"}", "{\"username\":\"nobody\",\"password\":\"Secret-Pass-1!\"}" }) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void meReturnsTheProfileForAValidTokenAndIs401Otherwise() throws Exception {
        String token = register("asha");

        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("asha"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.region").value("IN"));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Please log in to continue"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        // a tampered signature: change one character in the middle of it (appending or changing the very last character can
        // be ignored by base64 decoding, which would leave the signature bytes, and so the token, unchanged)
        String[] parts = token.substring("Bearer ".length()).split("[.]");
        String signature = parts[2];
        char middle = signature.charAt(10);
        String forgedSignature = signature.substring(0, 10) + (middle == 'A' ? 'B' : 'A') + signature.substring(11);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + parts[0] + "." + parts[1] + "." + forgedSignature))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenOfADeletedUserStopsWorking() throws Exception {
        String token = register("asha");
        userRepository.deleteAll();

        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }
}
