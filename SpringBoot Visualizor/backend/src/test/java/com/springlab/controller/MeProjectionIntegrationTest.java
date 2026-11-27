package com.springlab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.dto.user.UserResponse;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.LifecycleTraceFilter;
import com.springlab.lab.trace.RawTrace;
import com.springlab.lab.trace.TraceCollector;
import com.springlab.lab.trace.TraceContext;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/auth/me must read only the fields of its response: never the password hash or any other column. */
class MeProjectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static List<String> capturedSql(TraceCollector collector) {
        return collector.build("GET", "/x", false, List.of(), 200, null).sql().stream().map(RawTrace.SqlEvent::sql).toList();
    }

    @Test
    void theProfileQuerySelectsExactlyTheFourResponseColumnsAndNotThePasswordHash() {
        User sam = createUser("sam", Role.TEACHER);
        TraceCollector collector = new TraceCollector();
        TraceContext.bind(collector); // the same hook the lab uses to see the SQL Hibernate prepares
        UserResponse profile;
        try {
            profile = userRepository.findProfileByUsername("sam").orElseThrow();
        } finally {
            TraceContext.clear();
        }

        List<String> sql = capturedSql(collector);
        assertThat(profile).isEqualTo(new UserResponse(sam.getId(), "sam", "sam@example.com", Role.TEACHER));
        assertThat(sql).hasSize(1);
        String statement = sql.get(0).toLowerCase();
        assertThat(statement).contains("u1_0.id", "u1_0.username", "u1_0.email", "u1_0.role", "from users");
        assertThat(statement).doesNotContain("password").doesNotContain("password_hash").doesNotContain("enabled")
                .doesNotContain("created_at");
        assertThat(statement).as("the username is bound, not inlined").contains("?").doesNotContain("'sam'");
    }

    @Test
    void theOldFullEntityLookupWouldHaveSelectedThePasswordHash() {
        // Control for the test above: the same capture mechanism DOES see the hash column when the entity is loaded.
        createUser("sam", Role.STUDENT);
        entityManagerClear();
        TraceCollector collector = new TraceCollector();
        TraceContext.bind(collector);
        try {
            userRepository.findByUsername("sam").orElseThrow();
        } finally {
            TraceContext.clear();
        }

        assertThat(capturedSql(collector).get(0).toLowerCase()).contains("password_hash");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private void entityManagerClear() {
        entityManager.flush();
        entityManager.clear(); // force a real SELECT instead of a persistence-context hit
    }

    @Test
    void theEndpointReturnsExactlyIdUsernameEmailAndRole() throws Exception {
        User sam = createUser("sam", Role.TEACHER);

        MvcResult result = mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(sam)))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        Set<String> fields = new HashSet<>();
        Iterator<String> names = body.fieldNames();
        names.forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "username", "email", "role");
        assertThat(body.get("id").asLong()).isEqualTo(sam.getId());
        assertThat(body.get("username").asText()).isEqualTo("sam");
        assertThat(body.get("email").asText()).isEqualTo("sam@example.com");
        assertThat(body.get("role").asText()).isEqualTo("TEACHER");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password").doesNotContain("bcrypt");
    }

    @Test
    void theRealRequestsSqlDoesNotSelectThePasswordHashEitherCapturedByTheLabTrace() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String id = mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(sam))
                .header(LifecycleTraceFilter.REQUEST_HEADER, traceTicket(sam))).andReturn().getResponse()
                .getHeader(LifecycleTraceFilter.RESPONSE_HEADER);

        LifecycleTrace trace = traceStore.findOwned(id, sam.getId()).orElseThrow();

        assertThat(trace.sqlCount()).isEqualTo(2); // unchanged: the role lookup and the profile lookup
        assertThat(trace.sql()).extracting(LifecycleTrace.SqlStatement::trigger)
                .containsExactly("UserRepository.findAuthInfoByUsername", "UserRepository.findProfileByUsername");
        assertThat(trace.sql()).allSatisfy(s -> assertThat(s.sql().toLowerCase()).doesNotContain("password"));
    }

    @Test
    void databaseBackedAuthenticationAndAuthorizationAreUnchanged() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String token = bearer(sam);
        mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());

        sam.setRole(Role.TEACHER); // a role change shows up on the next request, with the same token
        userRepository.saveAndFlush(sam);
        entityManagerClear();
        mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());
        MvcResult after = mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andReturn();
        assertThat(objectMapper.readTree(after.getResponse().getContentAsString()).get("role").asText()).isEqualTo("TEACHER");

        sam.setEnabled(false); // a disabled account is refused immediately
        userRepository.saveAndFlush(sam);
        mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void theIdLookupReturnsTheStableIdAndNothingForUnknownUsers() {
        User sam = createUser("sam", Role.STUDENT);

        assertThat(userRepository.findIdByUsername("sam")).contains(sam.getId());
        assertThat(userRepository.findIdByUsername("nobody")).isEmpty();
    }
}
