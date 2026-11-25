package com.company.secureapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import com.jayway.jsonpath.JsonPath;

/**
 * Role checks, written in the two styles Spring Security Test offers:
 *   @WithMockUser    puts a fake user straight into the SecurityContext (no token, no database):
 *                    fast, tests ONLY the authorization rule
 *   real JWT login   goes through login + JWT filter + rules: tests the whole path
 */
class RoleAuthorizationTest extends SecurityTestSupport {

    private static String newEmployee() {
        return """
                {"name":"Test Person","email":"%s@company.com","department":"Finance",
                 "designation":"Analyst","salary":60000,"managerId":1}""".formatted(UUID.randomUUID());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void user_onAnAllowedEndpoint_is200() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void user_onAnAdminOnlyEndpoint_is403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithAnonymousUser
    void anonymous_onAProtectedEndpoint_is401() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR")
    void hr_createsAnEmployee_201() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(newEmployee()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Person"))
                .andExpect(jsonPath("$.salary").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void user_cannotCreateAnEmployee_403() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(newEmployee()))
                .andExpect(status().isForbidden());
    }

    /** End to end with real JWTs: HR creates, HR may NOT delete, ADMIN deletes, then it is gone. */
    @Test
    void admin_deletesAnEmployee_withRealTokens() throws Exception {
        String created = mockMvc.perform(post("/api/employees").header(HttpHeaders.AUTHORIZATION, bearer("hana"))
                        .contentType(MediaType.APPLICATION_JSON).content(newEmployee()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mockMvc.perform(delete("/api/employees/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer("hana")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/employees/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/employees/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isNotFound());
    }
}
