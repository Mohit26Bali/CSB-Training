package com.springlab.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.LifecycleTraceFilter;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trace ownership with two independently authenticated users (and an administrator): a trace belongs to the user, by
 * stable database id, who obtained the ticket for the traced request, and to nobody else.
 */
class TraceOwnershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String startTrace(User owner, String ticketOrNull, String path, String authorization) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (ticketOrNull != null) {
            request.header(LifecycleTraceFilter.REQUEST_HEADER, ticketOrNull);
        }
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        return response.getHeader(LifecycleTraceFilter.RESPONSE_HEADER);
    }

    /** Alice traces GET /api/auth/me with her own token and returns the trace id. */
    private String tracedMe(User user) throws Exception {
        String id = startTrace(user, traceTicket(user), "/api/auth/me", bearer(user));
        assertThat(id).as("trace id for %s", user.getUsername()).isNotNull();
        return id;
    }

    private MvcResult read(User reader, String traceId) throws Exception {
        return mockMvc.perform(get("/api/lab/traces/" + traceId).header("Authorization", bearer(reader))).andReturn();
    }

    // ------------------------------------------------------------------ the core rule

    @Test
    void theOwnerCanReadTheirTraceAndAnotherAuthenticatedUserGetsA404() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);
        String id = tracedMe(alice);

        MvcResult asAlice = read(alice, id);
        assertThat(asAlice.getResponse().getStatus()).isEqualTo(200);
        JsonNode trace = objectMapper.readTree(asAlice.getResponse().getContentAsString());
        assertThat(trace.get("id").asText()).isEqualTo(id);
        assertThat(trace.toString()).contains("alice");

        MvcResult asBob = read(bob, id);
        assertThat(asBob.getResponse().getStatus()).isEqualTo(404);
        assertThat(asBob.getResponse().getContentType()).contains("problem+json");
        assertThat(asBob.getResponse().getContentAsString()).doesNotContain("alice").doesNotContain("steps");
        // The owner is unaffected by someone else's failed attempt.
        assertThat(read(alice, id).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anotherUsersAttemptIsIndistinguishableFromAnUnknownTrace() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);
        String id = tracedMe(alice);

        MvcResult someoneElses = read(bob, id);
        MvcResult unknown = read(bob, UUID.randomUUID().toString());

        assertThat(someoneElses.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus()).isEqualTo(404);
        JsonNode a = objectMapper.readTree(someoneElses.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(unknown.getResponse().getContentAsString());
        assertThat(a.get("detail")).isEqualTo(b.get("detail")); // same message: nothing reveals that the trace exists
        assertThat(a.get("title")).isEqualTo(b.get("title"));
    }

    @Test
    void evenAnAdministratorCannotReadSomeoneElsesTrace() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User root = createUser("root", Role.ADMIN);
        String id = tracedMe(alice);

        assertThat(read(root, id).getResponse().getStatus()).isEqualTo(404);
        assertThat(read(alice, id).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anUnauthenticatedCallerGets401NotA404() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        String id = tracedMe(alice);

        mockMvc.perform(get("/api/lab/traces/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/traces/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void eachOfTwoUsersSeesOnlyTheirOwnTraceAndTheirOwnPrincipal() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.TEACHER);
        String aliceTrace = tracedMe(alice);
        String bobTrace = tracedMe(bob);

        assertThat(aliceTrace).isNotEqualTo(bobTrace);
        assertThat(read(alice, aliceTrace).getResponse().getStatus()).isEqualTo(200);
        assertThat(read(bob, bobTrace).getResponse().getStatus()).isEqualTo(200);
        assertThat(read(alice, bobTrace).getResponse().getStatus()).isEqualTo(404);
        assertThat(read(bob, aliceTrace).getResponse().getStatus()).isEqualTo(404);
        String alicesJson = read(alice, aliceTrace).getResponse().getContentAsString();
        String bobsJson = read(bob, bobTrace).getResponse().getContentAsString();
        assertThat(alicesJson).contains("alice").doesNotContain("bob");
        assertThat(bobsJson).contains("bob").doesNotContain("alice");
    }

    @Test
    void ownershipIsRecordedAgainstTheStableDatabaseId() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);
        String id = tracedMe(alice);

        assertThat(traceStore.findOwned(id, alice.getId())).isPresent();
        assertThat(traceStore.findOwned(id, bob.getId())).isEmpty();
        assertThat(traceStore.findOwned(id, 0L)).isEmpty();
    }

    // ------------------------------------------------------------------ ownership comes from the ticket, not the traced request

    @Test
    void aTracedRequestWithoutAnyTokenIsStillOwnedByWhoeverHeldTheTicket() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);

        String id = startTrace(alice, traceTicket(alice), "/api/auth/me", null); // the traced request is anonymous (401)

        assertThat(id).isNotNull();
        MvcResult asAlice = read(alice, id);
        assertThat(asAlice.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(asAlice.getResponse().getContentAsString()).get("status").asInt()).isEqualTo(401);
        assertThat(read(bob, id).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void usingSomeoneElsesTicketMakesThemTheOwnerNotYou() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);
        String alicesTicket = traceTicket(alice);

        String id = startTrace(bob, alicesTicket, "/api/auth/me", bearer(bob)); // Bob's request, Alice's ticket

        assertThat(id).isNotNull();
        assertThat(read(bob, id).getResponse().getStatus()).isEqualTo(404); // Bob cannot read it...
        assertThat(read(alice, id).getResponse().getStatus()).isEqualTo(200); // ...it is Alice's
    }

    @Test
    void ticketsAreSingleUseAndRequestsWithoutAValidTicketAreNotTraced() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        String ticket = traceTicket(alice);

        assertThat(startTrace(alice, ticket, "/api/topics", bearer(alice))).isNotNull();
        assertThat(startTrace(alice, ticket, "/api/topics", bearer(alice))).as("reused ticket").isNull();
        for (String bogus : new String[]{null, "1", "true", "garbage", UUID.randomUUID().toString()}) {
            assertThat(startTrace(alice, bogus, "/api/topics", bearer(alice))).as("ticket %s", bogus).isNull();
        }
    }

    @Test
    void ticketsAreIssuedOnlyToAuthenticatedUsersAndNeverLeakIntoLogOutput() throws Exception {
        User alice = createUser("alice", Role.STUDENT);

        mockMvc.perform(post("/api/lab/traces/tickets").header("Authorization", bearer(alice)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ticket").isString())
                .andExpect(jsonPath("$.expiresInSeconds").value(60));
        assertThat(new com.springlab.lab.web.LabController.TraceTicket("secret-ticket", 60).toString())
                .doesNotContain("secret-ticket");
        // A disabled account cannot obtain a ticket (its token is refused by the authentication step).
        alice.setEnabled(false);
        userRepository.saveAndFlush(alice);
        mockMvc.perform(post("/api/lab/traces/tickets").header("Authorization", bearer(alice)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownershipAlsoCoversTheJwtSimulatorsLabEndpoints() throws Exception {
        User alice = createUser("alice", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);
        MvcResult issued = mockMvc.perform(post("/api/lab/jwt/issue").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"VALID\"}")).andReturn();
        String labToken = objectMapper.readTree(issued.getResponse().getContentAsString()).get("token").asText();

        String id = startTrace(alice, traceTicket(alice), "/api/lab/secure/whoami", "Bearer " + labToken);

        assertThat(id).isNotNull();
        assertThat(read(alice, id).getResponse().getStatus()).isEqualTo(200);
        assertThat(read(bob, id).getResponse().getStatus()).isEqualTo(404);
        LifecycleTrace stored = traceStore.findOwned(id, alice.getId()).orElseThrow();
        assertThat(stored.steps()).isNotEmpty();
    }
}
