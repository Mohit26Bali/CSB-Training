package com.springlab.config;

import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBootstrapIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_PASSWORD = "correct-horse-battery";

    private AdminBootstrap bootstrap(String username, String email, String password) {
        return new AdminBootstrap(new BootstrapAdminProperties(username, email, password), userRepository,
                passwordEncoder);
    }

    @Test
    void createsNothingWhenNotConfigured() {
        bootstrap("", "", "").seed();
        bootstrap(null, null, null).seed();

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void createsAdministratorWithHashedPasswordWhoCanLogIn() throws Exception {
        bootstrap("siteadmin", "Admin@Example.com", ADMIN_PASSWORD).seed();

        User admin = userRepository.findByUsername("siteadmin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}$2").doesNotContain(ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"siteadmin\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void isIdempotentAndNeverResetsAnExistingAdministratorsPassword() {
        bootstrap("siteadmin", "admin@example.com", ADMIN_PASSWORD).seed();
        String originalHash = userRepository.findByUsername("siteadmin").orElseThrow().getPasswordHash();

        bootstrap("siteadmin", "admin@example.com", "a-completely-different-password").seed();
        bootstrap("another", "another@example.com", ADMIN_PASSWORD).seed();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByUsername("siteadmin").orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void partialConfigurationFailsFast() {
        assertThatThrownBy(() -> bootstrap("siteadmin", "", ADMIN_PASSWORD).seed())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must all be set");
        assertThatThrownBy(() -> bootstrap("siteadmin", "admin@example.com", null).seed())
                .isInstanceOf(IllegalStateException.class);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsWeakOrMalformedValuesWithoutEchoingThePassword() {
        assertThatThrownBy(() -> bootstrap("siteadmin", "admin@example.com", "short").seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12").hasMessageNotContaining("short");
        assertThatThrownBy(() -> bootstrap("x", "admin@example.com", ADMIN_PASSWORD).seed())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap("siteadmin", "not-an-email", ADMIN_PASSWORD).seed())
                .isInstanceOf(IllegalStateException.class);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void refusesToPromoteOrOverwriteAnExistingNonAdminAccount() {
        createUser("siteadmin", Role.STUDENT);

        assertThatThrownBy(() -> bootstrap("siteadmin", "new@example.com", ADMIN_PASSWORD).seed())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already used");
        assertThat(userRepository.findByUsername("siteadmin").orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void recoversWhenOnlyDisabledAdministratorsExist() {
        User dormant = createUser("old-admin", Role.ADMIN);
        dormant.setEnabled(false);
        userRepository.saveAndFlush(dormant);

        bootstrap("siteadmin", "admin@example.com", ADMIN_PASSWORD).seed();

        assertThat(userRepository.countByRoleAndEnabled(Role.ADMIN, true)).isEqualTo(1);
    }

    @Test
    void propertiesToStringMasksThePassword() {
        assertThat(new BootstrapAdminProperties("a", "b@c.de", ADMIN_PASSWORD).toString()).doesNotContain(ADMIN_PASSWORD);
    }
}
