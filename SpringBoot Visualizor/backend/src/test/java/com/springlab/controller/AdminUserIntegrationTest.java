package com.springlab.controller;

import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.service.AdminUserService;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminUserService adminUserService;

    private ResultActions send(MockHttpServletRequestBuilder request, User asUser) throws Exception {
        if (asUser != null) {
            request.header("Authorization", bearer(asUser));
        }
        return mockMvc.perform(request);
    }

    private static MockHttpServletRequestBuilder setRole(Long id, String role) {
        return patch("/api/admin/users/" + id + "/role").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\"}");
    }

    private static MockHttpServletRequestBuilder setEnabled(Long id, boolean enabled) {
        return patch("/api/admin/users/" + id + "/enabled").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}");
    }

    private void expectProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- server-side authorization ----------

    @Test
    void everyAdminEndpointRequiresAuthenticationAndAdminRole() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User teacher = createUser("tina", Role.TEACHER);
        User student = createUser("sam", Role.STUDENT);
        Long id = student.getId();

        for (var builder : new java.util.function.Supplier[]{
                () -> get("/api/admin/users"),
                () -> get("/api/admin/users/" + id),
                () -> setRole(id, "TEACHER"),
                () -> setEnabled(id, false)}) {
            @SuppressWarnings("unchecked")
            var supplier = (java.util.function.Supplier<MockHttpServletRequestBuilder>) builder;
            expectProblem(send(supplier.get(), null), 401);
            expectProblem(send(supplier.get(), student), 403);
            expectProblem(send(supplier.get(), teacher), 403);
        }
        send(get("/api/admin/users"), admin).andExpect(status().isOk());
        // The rejected calls must not have changed anything.
        assertThat(userRepository.findById(id).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
        assertThat(userRepository.findById(id).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void forgedAdminClaimInTokenDoesNotGrantAdminAccess() throws Exception {
        // jwtService writes role=STUDENT; here a student with a token is simply not an admin in the database.
        User student = createUser("sam", Role.STUDENT);
        expectProblem(send(get("/api/admin/users"), student), 403);
    }

    // ---------- listing / pagination ----------

    @Test
    void listsUsersWithPaginationAndStableSorting() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        for (String name : new String[]{"anna", "bert", "cora", "dave", "emil"}) {
            createUser(name, Role.STUDENT);
        }

        send(get("/api/admin/users?page=1&size=2&sort=username,asc"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].username").value("cora"))
                .andExpect(jsonPath("$.content[1].username").value("dave"));
    }

    @Test
    void listNeverExposesPasswordHashes() throws Exception {
        User admin = createUser("root", Role.ADMIN);

        send(get("/api/admin/users"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("root"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("bcrypt"))));
    }

    @Test
    void rejectsSortingByNonWhitelistedProperty() throws Exception {
        User admin = createUser("root", Role.ADMIN);

        expectProblem(send(get("/api/admin/users?sort=passwordHash,asc"), admin), 400);
        expectProblem(send(get("/api/admin/users?sort=doesNotExist"), admin), 400);
    }

    @Test
    void clampsPageSizeAndFiltersByRoleAndSearch() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        createUser("tina", Role.TEACHER);
        createUser("tom", Role.TEACHER);
        createUser("sam", Role.STUDENT);

        send(get("/api/admin/users?size=100000"), admin)
                .andExpect(jsonPath("$.size").value(100));
        send(get("/api/admin/users?role=TEACHER&sort=username,asc"), admin)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].username").value("tina"));
        send(get("/api/admin/users?q=SAM"), admin)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("sam"));
        send(get("/api/admin/users?role=TEACHER&q=tom"), admin)
                .andExpect(jsonPath("$.totalElements").value(1));
        expectProblem(send(get("/api/admin/users?role=SUPERUSER"), admin), 400);
    }

    @Test
    void getsSingleUserOr404() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User sam = createUser("sam", Role.STUDENT);

        send(get("/api/admin/users/" + sam.getId()), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("sam"));
        expectProblem(send(get("/api/admin/users/999999999"), admin), 404);
        expectProblem(send(get("/api/admin/users/not-a-number"), admin), 400);
    }

    // ---------- role changes ----------

    @Test
    void adminChangesRoleOfAnotherUser() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User sam = createUser("sam", Role.STUDENT);

        send(setRole(sam.getId(), "TEACHER"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        userRepository.flush();
        assertThat(userRepository.findById(sam.getId()).orElseThrow().getRole()).isEqualTo(Role.TEACHER);
    }

    @Test
    void roleChangeValidatesInput() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User sam = createUser("sam", Role.STUDENT);

        expectProblem(send(setRole(sam.getId(), "SUPERUSER"), admin), 400);
        expectProblem(send(patch("/api/admin/users/" + sam.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), admin), 400);
        expectProblem(send(patch("/api/admin/users/" + sam.getId() + "/enabled")
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":null}"), admin), 400);
        expectProblem(send(setRole(999999999L, "TEACHER"), admin), 404);
        expectProblem(send(setEnabled(999999999L, false), admin), 404);
    }

    @Test
    void demotedAdminLosesAdminAccessImmediatelyWithTheSameToken() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User other = createUser("second", Role.ADMIN);
        String othersOldToken = bearer(other);
        mockMvc.perform(get("/api/admin/users").header("Authorization", othersOldToken)).andExpect(status().isOk());

        send(setRole(other.getId(), "STUDENT"), admin).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").header("Authorization", othersOldToken))
                .andExpect(status().isForbidden());
    }

    // ---------- enable / disable ----------

    @Test
    void disablingAccountBlocksLoginAndExistingTokensImmediately() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User sam = createUser("sam", Role.STUDENT);
        String samsToken = bearer(sam);
        mockMvc.perform(get("/api/auth/me").header("Authorization", samsToken)).andExpect(status().isOk());

        send(setEnabled(sam.getId(), false), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

        expectProblem(mockMvc.perform(get("/api/auth/me").header("Authorization", samsToken)), 401);
        expectProblem(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sam\",\"password\":\"" + PASSWORD + "\"}")), 401);

        send(setEnabled(sam.getId(), true), admin).andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(get("/api/auth/me").header("Authorization", samsToken)).andExpect(status().isOk());
    }

    // ---------- self-protection ----------

    @Test
    void administratorCannotDemoteOrDisableThemselves() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        createUser("second", Role.ADMIN); // not the last admin: only the self-rule can be responsible

        expectProblem(send(setRole(admin.getId(), "TEACHER"), admin), 409);
        expectProblem(send(setRole(admin.getId(), "STUDENT"), admin), 409);
        expectProblem(send(setEnabled(admin.getId(), false), admin), 409);

        userRepository.flush();
        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void settingOwnStateToTheCurrentValueIsAllowed() throws Exception {
        User admin = createUser("root", Role.ADMIN);

        send(setRole(admin.getId(), "ADMIN"), admin).andExpect(status().isOk());
        send(setEnabled(admin.getId(), true), admin).andExpect(status().isOk());
    }

    @Test
    void administratorCanDemoteAndDisableAnotherAdminWhenOthersRemain() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User second = createUser("second", Role.ADMIN);
        User third = createUser("third", Role.ADMIN);

        send(setRole(second.getId(), "TEACHER"), admin).andExpect(status().isOk());
        send(setEnabled(third.getId(), false), admin).andExpect(status().isOk());
    }

    // ---------- last active administrator (service-level: the invariant must hold for ANY caller) ----------

    @Test
    void lastActiveAdministratorCannotBeDemotedOrDisabled() {
        User onlyAdmin = createUser("root", Role.ADMIN);
        createUser("tina", Role.TEACHER); // a different acting user, so the self-rule is not the reason

        assertThatThrownBy(() -> adminUserService.changeRole(onlyAdmin.getId(), Role.STUDENT, "tina"))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("last active administrator");
        assertThatThrownBy(() -> adminUserService.changeEnabled(onlyAdmin.getId(), false, "tina"))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("last active administrator");
        assertThat(userRepository.findById(onlyAdmin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void disabledAdministratorsDoNotCountAsActive() {
        User active = createUser("root", Role.ADMIN);
        User dormant = createUser("dormant", Role.ADMIN);
        dormant.setEnabled(false);
        userRepository.saveAndFlush(dormant);
        createUser("tina", Role.TEACHER);

        // Only one ACTIVE admin exists even though two ADMIN rows do.
        assertThatThrownBy(() -> adminUserService.changeRole(active.getId(), Role.STUDENT, "tina"))
                .isInstanceOf(BusinessRuleViolationException.class);
        // Changing the already-inactive admin cannot reduce the number of active administrators.
        assertThat(adminUserService.changeRole(dormant.getId(), Role.STUDENT, "tina").role()).isEqualTo(Role.STUDENT);
    }
}
