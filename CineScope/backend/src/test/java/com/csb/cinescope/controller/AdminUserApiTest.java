package com.csb.cinescope.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchlistItem;

/**
 * Account management for administrators through the real stack: who may call it (authorization), the full create / read /
 * update / delete, search and paging, the safety rules, and the effect on login tokens. Users registered by
 * {@code register(...)} have the password "Secret-Pass-1!"; administrators come from {@code registerAdmin(...)}.
 */
class AdminUserApiTest extends AbstractApiTest {

    @Override
    protected boolean signedInByDefault() {
        return false;
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        MockHttpServletRequestBuilder builder = request.header("Authorization", token);
        return json == null ? mvc.perform(builder) : mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private static String body(String displayName, String region, String role, boolean enabled) {
        return "{\"displayName\":\"" + displayName + "\",\"region\":\"" + region + "\",\"role\":\"" + role + "\",\"enabled\":" + enabled + "}";
    }

    private static String create(String username, String email, String password, String role) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"displayName\":\"New Person\",\"password\":\"" + password
                + "\",\"role\":\"" + role + "\"}";
    }

    // ---- authorization: who may call it ----

    @Test
    void aVisitorIs401AnOrdinaryUserIs403AndOnlyAnAdministratorGetsIn() throws Exception {
        String user = register("asha");
        String admin = registerAdmin("boss");
        long id = idOf("asha");
        String[] paths = { "/api/admin/users", "/api/admin/users/" + id };

        for (String path : paths) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            as(user, get(path), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
            as(admin, get(path), null).andExpect(status().isOk());
        }
        mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        as(user, post("/api/admin/users"), create("new.one", "new.one@example.com", "Violet-Rain-77!", "USER")).andExpect(status().isForbidden());
        as(user, put("/api/admin/users/" + id), body("Hacked", "IN", "ADMIN", true)).andExpect(status().isForbidden());
        as(user, delete("/api/admin/users/" + id), null).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + id)).andExpect(status().isUnauthorized());

        assertTrue(userRepository.findByUsername("asha").isPresent(), "the refused calls changed nothing");
        assertEquals(Role.USER, userRepository.findByUsername("asha").orElseThrow().getRole());
        assertEquals(2, userRepository.count(), "no account was created by the refused call");
    }

    @Test
    void anOrdinaryUserCannotMakeThemselvesAnAdministratorByAnyRoute() throws Exception {
        String user = register("asha");

        // the profile endpoint has no role field: extra fields are ignored
        as(user, put("/api/profile"), "{\"displayName\":\"Asha\",\"region\":\"IN\",\"role\":\"ADMIN\"}").andExpect(status().isOk());
        assertEquals(Role.USER, userRepository.findByUsername("asha").orElseThrow().getRole());
        // registration cannot set a role either
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sneaky\",\"email\":\"s@example.com\",\"password\":\"Violet-Rain-77!\",\"displayName\":\"S\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
        assertEquals(Role.USER, userRepository.findByUsername("sneaky").orElseThrow().getRole());
    }

    @Test
    void theAdministratorsLoginAnswerCarriesTheRole() throws Exception {
        registerAdmin("boss");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"boss\",\"password\":\"Secret-Pass-1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---- reading: list, search, paging ----

    @Test
    void theListIsSortedByUsernameWithWatchlistSizesAndNoPasswords() throws Exception {
        String admin = registerAdmin("boss");
        register("zed");
        register("asha");
        watchlistRepository.save(new WatchlistItem(userRepository.findByUsername("asha").orElseThrow(), 550, "movie", "Fight Club", "/fc.jpg", 1999));
        watchlistRepository.save(new WatchlistItem(userRepository.findByUsername("asha").orElseThrow(), 603, "movie", "The Matrix", "/m.jpg", 1999));

        as(admin, get("/api/admin/users"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(3))
                .andExpect(jsonPath("$.items[*].username", contains("asha", "boss", "zed")))
                .andExpect(jsonPath("$.items[0].watchlistCount").value(2))
                .andExpect(jsonPath("$.items[1].watchlistCount").value(0))
                .andExpect(jsonPath("$.items[1].role").value("ADMIN"))
                .andExpect(jsonPath("$.items[0].enabled").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].password").doesNotExist());
    }

    @Test
    void searchFiltersAndPagingWork() throws Exception {
        String admin = registerAdmin("boss");
        for (int i = 1; i <= 12; i++) {
            register(String.format("user%02d", i));
        }
        User disabled = userRepository.findByUsername("user03").orElseThrow();
        disabled.setEnabled(false);
        userRepository.save(disabled);

        as(admin, get("/api/admin/users?size=5"), null)
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalResults").value(13))
                .andExpect(jsonPath("$.page").value(1));
        as(admin, get("/api/admin/users?size=5&page=3"), null)
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].username", contains("user10", "user11", "user12")));
        as(admin, get("/api/admin/users?q=USER1"), null) // any letter case; matches username, email or name
                .andExpect(jsonPath("$.items[*].username", containsInAnyOrder("user10", "user11", "user12")));
        as(admin, get("/api/admin/users?q=user07@example"), null).andExpect(jsonPath("$.items[*].username", contains("user07")));
        as(admin, get("/api/admin/users?role=ADMIN"), null).andExpect(jsonPath("$.items[*].username", contains("boss")));
        as(admin, get("/api/admin/users?enabled=false"), null).andExpect(jsonPath("$.items[*].username", contains("user03")));
        as(admin, get("/api/admin/users?q=nobody-like-this"), null).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalPages").value(1));
        as(admin, get("/api/admin/users").param("q", "100%"), null).andExpect(jsonPath("$.items.length()").value(0)); // % is text, not a wildcard
    }

    @Test
    void badListParametersAre400() throws Exception {
        String admin = registerAdmin("boss");

        for (String url : new String[] { "/api/admin/users?page=0", "/api/admin/users?size=0", "/api/admin/users?size=51", "/api/admin/users?role=KING",
                "/api/admin/users?enabled=maybe", "/api/admin/users?page=abc" }) {
            as(admin, get(url), null).andExpect(status().isBadRequest());
        }
    }

    @Test
    void oneAccountIsShownByIdAndAnUnknownIdIs404() throws Exception {
        String admin = registerAdmin("boss");
        register("asha");

        as(admin, get("/api/admin/users/" + idOf("asha")), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("asha"))
                .andExpect(jsonPath("$.email").value("asha@example.com"));
        as(admin, get("/api/admin/users/999999"), null).andExpect(status().isNotFound());
    }

    // ---- creating ----

    @Test
    void anAdministratorCreatesAccountsWithARoleAndTheyCanSignIn() throws Exception {
        String admin = registerAdmin("boss");

        as(admin, post("/api/admin/users"), create("New.Hire", "New.Hire@Example.com", "Violet-Rain-77!", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.hire"))
                .andExpect(jsonPath("$.email").value("new.hire@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.region").value("IN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.watchlistCount").value(0));

        String token = login("new.hire", "Violet-Rain-77!");
        as(token, get("/api/admin/users"), null).andExpect(status().isOk()); // the new administrator really is one
        as(admin, post("/api/admin/users"), create("plain", "plain@example.com", "Violet-Rain-77!", "USER")).andExpect(status().isCreated());
        as(login("plain", "Violet-Rain-77!"), get("/api/admin/users"), null).andExpect(status().isForbidden());
    }

    @Test
    void creatingFollowsTheSignUpRulesAndRefusesDuplicates() throws Exception {
        String admin = registerAdmin("boss");
        register("asha");

        as(admin, post("/api/admin/users"), create("weak.one", "weak@example.com", "password1", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("password must include an uppercase letter and a symbol such as ! @ # or -"));
        as(admin, post("/api/admin/users"), create("meera.k", "other@example.com", "My-meera.k-pass9!", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not contain your username"));
        as(admin, post("/api/admin/users"), create("ASHA", "fresh@example.com", "Violet-Rain-77!", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username 'asha' is already taken"));
        as(admin, post("/api/admin/users"), create("fresh", "ASHA@example.com", "Violet-Rain-77!", "USER"))
                .andExpect(status().isConflict());
        as(admin, post("/api/admin/users"), "{\"username\":\"fresh\",\"email\":\"fresh@example.com\",\"displayName\":\"F\",\"password\":\"Violet-Rain-77!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").value("role is required"));
        as(admin, post("/api/admin/users"), create("fresh", "fresh@example.com", "Violet-Rain-77!", "KING")).andExpect(status().isBadRequest());
        assertEquals(2, userRepository.count(), "none of the refused calls created an account");
    }

    // ---- updating ----

    @Test
    void anAdministratorChangesNameCountryRoleAndEnabled() throws Exception {
        String admin = registerAdmin("boss");
        register("asha");

        as(admin, put("/api/admin/users/" + idOf("asha")), body("Asha Rao", "gb", "ADMIN", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Asha Rao"))
                .andExpect(jsonPath("$.region").value("GB"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User asha = userRepository.findByUsername("asha").orElseThrow();
        assertEquals(Role.ADMIN, asha.getRole());
        assertEquals("asha@example.com", asha.getEmail(), "username and email are not changed here");
        as(admin, put("/api/admin/users/999999"), body("X", "IN", "USER", true)).andExpect(status().isNotFound());
        as(admin, put("/api/admin/users/" + idOf("asha")), body("", "India", "USER", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.region").exists());
    }

    @Test
    void disablingAnAccountLocksThePersonOutAtOnceEvenWithAValidToken() throws Exception {
        String admin = registerAdmin("boss");
        String token = register("asha");
        mvc.perform(get("/api/profile").header("Authorization", token)).andExpect(status().isOk());

        as(admin, put("/api/admin/users/" + idOf("asha")), body("asha Display", "IN", "USER", false)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/profile").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"asha\",\"password\":\"Secret-Pass-1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password")); // the same message: no hint that the account exists

        as(admin, put("/api/admin/users/" + idOf("asha")), body("asha Display", "IN", "USER", true)).andExpect(status().isOk());
        login("asha", "Secret-Pass-1!"); // enabled again: signs in
        mvc.perform(get("/api/profile").header("Authorization", token)).andExpect(status().isUnauthorized()); // but the OLD token stays dead
    }

    @Test
    void changingARoleEndsTheirSessionsAndTheNewRightsApplyAtOnce() throws Exception {
        String admin = registerAdmin("boss");
        String oldToken = register("asha");
        as(oldToken, get("/api/admin/users"), null).andExpect(status().isForbidden());

        as(admin, put("/api/admin/users/" + idOf("asha")), body("asha Display", "IN", "ADMIN", true)).andExpect(status().isOk());

        mvc.perform(get("/api/profile").header("Authorization", oldToken)).andExpect(status().isUnauthorized()); // must sign in again
        String newToken = login("asha", "Secret-Pass-1!");
        as(newToken, get("/api/admin/users"), null).andExpect(status().isOk());

        as(admin, put("/api/admin/users/" + idOf("asha")), body("asha Display", "IN", "USER", true)).andExpect(status().isOk()); // and back
        as(newToken, get("/api/admin/users"), null).andExpect(status().isUnauthorized()); // the admin-era token no longer works at all
        as(login("asha", "Secret-Pass-1!"), get("/api/admin/users"), null).andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorCannotDemoteOrDisableThemselves() throws Exception {
        String boss = registerAdmin("boss");
        registerAdmin("second");
        long bossId = idOf("boss");

        as(boss, put("/api/admin/users/" + bossId), body("boss Display", "IN", "USER", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot remove your own administrator rights or disable your own account"));
        as(boss, put("/api/admin/users/" + bossId), body("boss Display", "IN", "ADMIN", false)).andExpect(status().isBadRequest());
        as(boss, put("/api/admin/users/" + bossId), body("Renamed Boss", "IN", "ADMIN", true)).andExpect(status().isOk()); // other changes are fine

        User stored = userRepository.findByUsername("boss").orElseThrow();
        assertEquals(Role.ADMIN, stored.getRole());
        assertTrue(stored.isEnabled());
        assertEquals("Renamed Boss", stored.getDisplayName());
    }

    // ---- deleting ----

    @Test
    void deletingAnAccountRemovesItsWatchlistAndKillsItsToken() throws Exception {
        String admin = registerAdmin("boss");
        String token = register("asha");
        register("ravi");
        watchlistRepository.save(new WatchlistItem(userRepository.findByUsername("asha").orElseThrow(), 550, "movie", "Fight Club", "/fc.jpg", 1999));
        watchlistRepository.save(new WatchlistItem(userRepository.findByUsername("ravi").orElseThrow(), 550, "movie", "Fight Club", "/fc.jpg", 1999));

        as(admin, delete("/api/admin/users/" + idOf("asha")), null).andExpect(status().isNoContent());

        assertFalse(userRepository.findByUsername("asha").isPresent());
        assertEquals(1, watchlistRepository.count(), "ravi's entry is untouched");
        mvc.perform(get("/api/profile").header("Authorization", token)).andExpect(status().isUnauthorized());
        as(admin, get("/api/admin/users/" + idOf("ravi")), null).andExpect(jsonPath("$.watchlistCount").value(1));
        as(admin, delete("/api/admin/users/999999"), null).andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorCannotDeleteThemselvesHereButCanDeleteAnotherAdministrator() throws Exception {
        String boss = registerAdmin("boss");
        registerAdmin("second");

        as(boss, delete("/api/admin/users/" + idOf("boss")), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot delete your own account here. Use your profile page"));
        as(boss, delete("/api/admin/users/" + idOf("second")), null).andExpect(status().isNoContent());
        assertTrue(userRepository.findByUsername("boss").isPresent());
    }
}
