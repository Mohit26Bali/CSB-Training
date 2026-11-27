package com.springlab.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.config.JwtProperties;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.Environment;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrameworkMetaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Environment environment;
    @Autowired
    private JwtProperties jwtProperties;

    private JsonNode fetchMeta() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        MvcResult result = mockMvc.perform(get("/api/lab/meta").header("Authorization", bearer(sam)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/lab/meta")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lab/code-references")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lab/lifecycle/catalog")).andExpect(status().isUnauthorized());
    }

    @Test
    void reportedVersionsAreTheOnesActuallyOnTheClasspath() throws Exception {
        JsonNode versions = fetchMeta().get("versions");

        assertThat(versions.get("springBoot").asText()).isEqualTo(SpringBootVersion.getVersion()).startsWith("3.");
        assertThat(versions.get("springFramework").asText()).isEqualTo(SpringVersion.getVersion());
        assertThat(versions.get("springSecurity").asText()).isEqualTo(SpringSecurityCoreVersion.getVersion());
        assertThat(versions.get("hibernate").asText()).isEqualTo(org.hibernate.Version.getVersionString())
                .startsWith("6.");
        assertThat(versions.get("java").asText()).isEqualTo(Runtime.version().toString());
    }

    @Test
    void reportedSettingsAreTheEffectiveConfiguration() throws Exception {
        JsonNode settings = fetchMeta().get("settings");

        assertThat(settings.get("spring.jpa.open-in-view").asText())
                .isEqualTo(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(settings.get("spring.jpa.hibernate.ddl-auto").asText())
                .isEqualTo(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(settings.get("hibernate.dialect").asText()).containsIgnoringCase("MySQL");
        assertThat(settings.get("jwt.accessTokenLifetime").asText()).isEqualTo(jwtProperties.accessTokenTtl().toString());
    }

    @Test
    void onlyAnAllowListOfFactsIsExposedAndNoSecretsLeak() throws Exception {
        MvcResult result;
        User sam = createUser("sam", Role.STUDENT);
        result = mockMvc.perform(get("/api/lab/meta").header("Authorization", bearer(sam))).andReturn();
        String json = result.getResponse().getContentAsString();
        JsonNode meta = objectMapper.readTree(json);

        assertThat(keys(meta)).containsExactlyInAnyOrder("versions", "settings");
        assertThat(keys(meta.get("versions"))).containsExactlyInAnyOrder("java", "springBoot", "springFramework",
                "springSecurity", "hibernate");
        assertThat(keys(meta.get("settings"))).containsExactlyInAnyOrder("spring.jpa.open-in-view",
                "spring.jpa.hibernate.ddl-auto", "hibernate.dialect", "jwt.accessTokenLifetime", "jwt.signingAlgorithm");
        // Never the signing key, database credentials/URL, or anything resembling them.
        assertThat(json).doesNotContain(jwtProperties.secret())
                .doesNotContainIgnoringCase("jdbc").doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("secret").doesNotContainIgnoringCase("username=");
    }

    @Test
    void codeReferencesAndCatalogAreServedToAuthenticatedUsers() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String auth = bearer(sam);

        JsonNode refs = objectMapper.readTree(mockMvc.perform(get("/api/lab/code-references")
                .header("Authorization", auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode catalog = objectMapper.readTree(mockMvc.perform(get("/api/lab/lifecycle/catalog")
                .header("Authorization", auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(refs.size()).isGreaterThanOrEqualTo(10);
        assertThat(refs.get(0).has("excerpt")).isTrue();
        assertThat(catalog.get("targets").size()).isGreaterThanOrEqualTo(4);
        assertThat(catalog.get("experiments").size()).isGreaterThanOrEqualTo(5);
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(keys::add);
        return keys;
    }
}
