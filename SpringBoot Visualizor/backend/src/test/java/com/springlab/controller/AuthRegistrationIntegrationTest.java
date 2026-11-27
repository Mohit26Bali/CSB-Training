package com.springlab.controller;

import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRegistrationIntegrationTest extends AbstractIntegrationTest {

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String username, String email, String password) {
        return """
                {"username":"%s","email":"%s","password":"%s"}""".formatted(username, email, password);
    }

    @Test
    void registersStudentAndNeverExposesPassword() throws Exception {
        register(body("alice", "Alice@Example.com", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesBcryptHashNotPlaintext() throws Exception {
        register(body("alice", "alice@example.com", PASSWORD)).andExpect(status().isCreated());

        User stored = userRepository.findByUsername("alice").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void ignoresRoleSuppliedByClient() throws Exception {
        for (String role : new String[]{"ADMIN", "TEACHER"}) {
            String json = """
                    {"username":"user_%1$s","email":"%1$s@example.com","password":"%2$s","role":"%3$s","roles":["%3$s"]}"""
                    .formatted(role.toLowerCase(), PASSWORD, role);
            register(json)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("STUDENT"));
        }
        assertThat(userRepository.countByRole(Role.ADMIN)).isZero();
        assertThat(userRepository.countByRole(Role.TEACHER)).isZero();
        assertThat(userRepository.countByRole(Role.STUDENT)).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        register(body("alice", "alice@example.com", PASSWORD)).andExpect(status().isCreated());

        register(body("alice", "other@example.com", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Username is already taken"));
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() throws Exception {
        register(body("alice", "alice@example.com", PASSWORD)).andExpect(status().isCreated());

        register(body("alice2", "ALICE@example.com", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void returnsFieldErrorsForInvalidInput() throws Exception {
        register(body("a!", "not-an-email", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsMissingFieldsAndMalformedJson() throws Exception {
        register("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.username").exists());
        register("{not json").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimitInBytes() throws Exception {
        // 40 characters but 80 bytes in UTF-8: passes @Size, must fail the byte-length check.
        String multiByte = "é".repeat(40);
        register(body("alice", "alice@example.com", multiByte))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passwordWithinBcryptLimit").exists());
    }
}
