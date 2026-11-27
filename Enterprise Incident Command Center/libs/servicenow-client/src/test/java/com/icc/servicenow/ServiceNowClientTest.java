package com.icc.servicenow;

import com.icc.servicenow.ServiceNowException.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The client is tested against a mock of the Table API (D9): no live instance, no password. */
class ServiceNowClientTest {

    private static final String BASE = "https://dev1.service-now.com";

    private MockRestServiceServer server;
    private ServiceNowClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("Authorization", "Basic dGVzdDp0ZXN0");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ServiceNowClient(builder.build());
    }

    private static String error(String message, String detail) {
        return "{\"error\":{\"message\":\"" + message + "\",\"detail\":\"" + detail + "\"},\"status\":\"failure\"}";
    }

    // ----------------------------------------------------------------- create and update

    @Test
    void create_postsTheFieldsAsJson_withTheLogin_andReturnsTheRecord() {
        server.expect(requestTo(BASE + "/api/now/table/incident"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Basic dGVzdDp0ZXN0"))
                .andExpect(content().json("{\"short_description\":\"Switch down\",\"impact\":\"1\",\"correlation_id\":\"icc-12\"}"))
                .andRespond(withSuccess("{\"result\":{\"sys_id\":\"abc123\",\"number\":\"INC0010005\"}}", MediaType.APPLICATION_JSON));

        Map<String, Object> created = client.create("incident",
                Map.of("short_description", "Switch down", "impact", "1", "correlation_id", "icc-12"));

        assertEquals("abc123", ServiceNowClient.sysId(created));
        assertEquals("INC0010005", created.get("number"));
        server.verify();
    }

    @Test
    void update_patchesOnlyTheSentFields_ofThatRecord() {
        server.expect(requestTo(BASE + "/api/now/table/incident/abc123"))
                .andExpect(method(PATCH))
                .andExpect(content().json("{\"state\":\"2\"}", true))
                .andRespond(withSuccess("{\"result\":{\"sys_id\":\"abc123\",\"state\":\"2\"}}", MediaType.APPLICATION_JSON));

        Map<String, Object> updated = client.update("incident", "abc123", Map.of("state", "2"));

        assertEquals("2", updated.get("state"));
        server.verify();
    }

    // ----------------------------------------------------------------- find (the retry guard)

    @Test
    void findByCorrelationId_asksForOneRecordWithThatId() {
        server.expect(requestTo(startsWith(BASE + "/api/now/table/incident?")))
                .andExpect(method(GET))
                // The "=" inside the query value is sent as %3D; ServiceNow decodes it.
                .andExpect(queryParam("sysparm_query", "correlation_id%3Dicc-12"))
                .andExpect(queryParam("sysparm_limit", "1"))
                .andRespond(withSuccess("{\"result\":[{\"sys_id\":\"abc123\",\"number\":\"INC0010005\"}]}", MediaType.APPLICATION_JSON));

        Optional<Map<String, Object>> found = client.findByCorrelationId("incident", "icc-12");

        assertTrue(found.isPresent());
        assertEquals("abc123", ServiceNowClient.sysId(found.get()));
        server.verify();
    }

    @Test
    void findOne_whenNothingMatches_isEmpty() {
        server.expect(requestTo(startsWith(BASE + "/api/now/table/sys_user?")))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));

        assertFalse(client.findOne("sys_user", "email", "nobody@icc.com").isPresent());
    }

    // ----------------------------------------------------------------- errors become clear exceptions

    @Test
    void anUnknownLogin_isUnauthorized_andNotWorthRetrying() {
        server.expect(requestTo(BASE + "/api/now/table/incident")).andRespond(
                withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error("User Not Authenticated", "Required to provide Auth information")));

        ServiceNowException e = assertThrows(ServiceNowException.class, () -> client.create("incident", Map.of("a", "b")));

        assertEquals(Kind.UNAUTHORIZED, e.kind());
        assertEquals(401, e.status());
        assertFalse(e.isWorthRetrying());
        assertTrue(e.getMessage().contains("User Not Authenticated"));
    }

    @Test
    void aMissingRole_isForbidden() {
        server.expect(requestTo(BASE + "/api/now/table/problem")).andRespond(
                withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(error("Operation Failed", "ACL Exception Insert Failed due to security constraints")));

        ServiceNowException e = assertThrows(ServiceNowException.class, () -> client.create("problem", Map.of("a", "b")));

        assertEquals(Kind.FORBIDDEN, e.kind());
    }

    @Test
    void aMissingRecord_isNotFound() {
        server.expect(requestTo(BASE + "/api/now/table/incident/zzz")).andRespond(
                withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("No Record found", "Record does not exist")));

        assertEquals(Kind.NOT_FOUND, assertThrows(ServiceNowException.class, () -> client.update("incident", "zzz", Map.of("a", "b"))).kind());
    }

    @Test
    void theConfusingHttp500_isTranslatedIntoARejectedValue_withAClearMessage() {
        server.expect(requestTo(BASE + "/api/now/table/incident/abc123")).andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(error("IP may not be null", "")));

        ServiceNowException e = assertThrows(ServiceNowException.class, () -> client.update("incident", "abc123", Map.of("close_code", "Solved")));

        assertEquals(Kind.REJECTED_VALUE, e.kind());
        assertFalse(e.isWorthRetrying(), "a wrong value fails the same way every time");
        assertTrue(e.getMessage().contains("close code"), e.getMessage());
    }

    @Test
    void anotherServerError_isWorthRetrying() {
        server.expect(requestTo(BASE + "/api/now/table/incident")).andRespond(
                withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(error("Service Unavailable", "")));

        ServiceNowException e = assertThrows(ServiceNowException.class, () -> client.create("incident", Map.of("a", "b")));

        assertEquals(Kind.SERVER_ERROR, e.kind());
        assertTrue(e.isWorthRetrying());
    }

    @Test
    void anotherClientError_isABadRequest() {
        server.expect(requestTo(BASE + "/api/now/table/incident")).andRespond(
                withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(error("Invalid field", "")));

        assertEquals(Kind.BAD_REQUEST, assertThrows(ServiceNowException.class, () -> client.create("incident", Map.of("a", "b"))).kind());
    }

    @Test
    void aSleepingInstance_isUnreachable_andWorthRetrying() {
        server.expect(requestTo(BASE + "/api/now/table/incident")).andRespond(withException(new IOException("Connection refused")));

        ServiceNowException e = assertThrows(ServiceNowException.class, () -> client.create("incident", Map.of("a", "b")));

        assertEquals(Kind.UNREACHABLE, e.kind());
        assertEquals(0, e.status());
        assertTrue(e.isWorthRetrying());
    }

    // ----------------------------------------------------------------- small helpers

    @Test
    void valueOf_readsAReferenceField_andAPlainField() {
        assertEquals("u1", ServiceNowClient.valueOf(Map.of("link", "https://x/api/now/table/sys_user/u1", "value", "u1")));
        assertEquals("New", ServiceNowClient.valueOf("New"));
        assertNull(ServiceNowClient.valueOf(null));
    }

    @Test
    void theConfigNeverShowsThePassword() {
        ServiceNowConfig config = new ServiceNowConfig("https://dev1.service-now.com", "icc.integration", "s3cret-password");

        assertFalse(config.toString().contains("s3cret-password"));
        assertTrue(config.toString().contains("icc.integration"));
    }
}
