package com.csb.cinescope.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchlistItem;

/**
 * "My profile" through the real stack: reading and changing my own details, changing my password (and what that does to old
 * login tokens), and deleting my account. Every test registers its own users; the password of a registered user is
 * "Secret-Pass-1!".
 */
class ProfileApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Secret-Pass-1!";

    @Override
    protected boolean signedInByDefault() {
        return false; // each test signs in as the users it creates
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---- who can reach it ----

    @Test
    void everyProfileEndpointNeedsALogin() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profile/password").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void theProfileShowsMyDetailsAndMyRole() throws Exception {
        String token = register("asha");

        mvc.perform(get("/api/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("asha"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.displayName").value("asha Display"))
                .andExpect(jsonPath("$.region").value("IN"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ---- changing my details ----

    @Test
    void iCanChangeMyNameAndCountryAndOnlyMine() throws Exception {
        String asha = register("asha");
        register("ravi");

        send(put("/api/profile"), asha, "{\"displayName\":\"  Asha R.  \",\"region\":\"us\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Asha R."))
                .andExpect(jsonPath("$.region").value("US"));

        assertEquals("Asha R.", userRepository.findByUsername("asha").orElseThrow().getDisplayName());
        assertEquals("ravi Display", userRepository.findByUsername("ravi").orElseThrow().getDisplayName(), "nobody else was touched");
        assertEquals("asha@example.com", userRepository.findByUsername("asha").orElseThrow().getEmail(), "the email cannot be changed here");
    }

    @Test
    void badProfileChangesAreRefusedWithAMessagePerField() throws Exception {
        String token = register("asha");

        send(put("/api/profile"), token, "{\"displayName\":\"\",\"region\":\"India\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.region").value("region must be a two-letter country code such as IN or US"));
        send(put("/api/profile"), token, "{\"displayName\":\"" + "x".repeat(61) + "\",\"region\":\"IN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.displayName").value("display name must be at most 60 characters"));
        assertEquals("asha Display", userRepository.findByUsername("asha").orElseThrow().getDisplayName());
    }

    // ---- changing my password ----

    @Test
    void changingThePasswordWorksEndsOldSessionsAndGivesANewOne() throws Exception {
        String oldToken = register("asha");

        String json = send(put("/api/profile/password"), oldToken, "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Violet-Rain-77!\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("asha"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String newToken = "Bearer " + json.substring(json.indexOf("\"token\":\"") + 9, json.indexOf('"', json.indexOf("\"token\":\"") + 9));

        assertNotEquals(oldToken, newToken);
        mvc.perform(get("/api/profile").header("Authorization", oldToken)).andExpect(status().isUnauthorized()); // old session is over
        mvc.perform(get("/api/profile").header("Authorization", newToken)).andExpect(status().isOk()); // the new one works
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"asha\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized()); // old password is dead
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"asha\",\"password\":\"Violet-Rain-77!\"}"))
                .andExpect(status().isOk());
        User stored = userRepository.findByUsername("asha").orElseThrow();
        assertTrue(stored.getPassword().startsWith("$2"));
        assertEquals(1, stored.getTokenVersion());
    }

    @Test
    void aWrongCurrentPasswordIs400NotAnExpiredLoginAndChangesNothing() throws Exception {
        String token = register("asha");
        String hashBefore = userRepository.findByUsername("asha").orElseThrow().getPassword();

        send(put("/api/profile/password"), token, "{\"currentPassword\":\"Not-The-One-1!\",\"newPassword\":\"Violet-Rain-77!\"}")
                .andExpect(status().isBadRequest()) // a 401 would make the app sign the person out
                .andExpect(jsonPath("$.message").value("The current password is incorrect"));

        assertEquals(hashBefore, userRepository.findByUsername("asha").orElseThrow().getPassword());
        mvc.perform(get("/api/profile").header("Authorization", token)).andExpect(status().isOk()); // session still valid
    }

    @Test
    void theNewPasswordFollowsTheSignUpRules() throws Exception {
        String token = register("asha");
        String[][] cases = {
                { "password1", "password must include an uppercase letter and a symbol such as ! @ # or -" },
                { "Password1!", "password is too common; choose something harder to guess" },
                { "Sunset-Rain-42!abcdef", "password must be 8 to 20 characters" },
        };
        for (String[] c : cases) {
            send(put("/api/profile/password"), token, "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + c[0] + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").value(c[1]));
        }
        send(put("/api/profile/password"), token, "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"asha-Rain-77!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not contain your username"));
        send(put("/api/profile/password"), token, "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The new password must be different from the current one"));
        assertEquals(0, userRepository.findByUsername("asha").orElseThrow().getTokenVersion(), "no failed attempt ended the session");
    }

    // ---- deleting my account ----

    @Test
    void deletingTheAccountNeedsThePasswordAndRemovesTheAccountAndItsWatchlist() throws Exception {
        String token = register("asha");
        String other = register("ravi");
        User asha = userRepository.findByUsername("asha").orElseThrow();
        watchlistRepository.save(new WatchlistItem(asha, 550, "movie", "Fight Club", "/fc.jpg", 1999));
        watchlistRepository.save(new WatchlistItem(userRepository.findByUsername("ravi").orElseThrow(), 550, "movie", "Fight Club", "/fc.jpg", 1999));

        send(delete("/api/profile"), token, "{\"password\":\"wrong\"}").andExpect(status().isBadRequest());
        assertTrue(userRepository.findByUsername("asha").isPresent(), "a wrong password deletes nothing");

        send(delete("/api/profile"), token, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());

        assertFalse(userRepository.findByUsername("asha").isPresent());
        assertEquals(1, watchlistRepository.count(), "only asha's entry was removed");
        mvc.perform(get("/api/profile").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"asha\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile").header("Authorization", other)).andExpect(status().isOk());
    }

    @Test
    void theOnlyAdministratorCannotDeleteTheirOwnAccount() throws Exception {
        String admin = registerAdmin("boss");

        send(delete("/api/profile"), admin, "{\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You are the only administrator. Make someone else an administrator before deleting this account"));
        assertTrue(userRepository.findByUsername("boss").isPresent());
    }
}
