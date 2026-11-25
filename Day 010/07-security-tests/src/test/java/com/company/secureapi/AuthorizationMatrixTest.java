package com.company.secureapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The README authorization matrix: every role on every endpoint, with JWTs.
 *   Y = allowed (status given), - = 403 ACCESS_DENIED; no token = 401 AUTH_REQUIRED.
 * Employees: 1 admin, 2 hana (HR), 3 manoj (MANAGER of 4, 5), 4 asha, 5 ravi, 7 Karan.
 */
class AuthorizationMatrixTest extends SecurityTestSupport {

    private static final List<String> USERS = List.of("admin", "hana", "manoj", "asha", "ravi");

    private static final String NEW_EMPLOYEE = """
            {"name":"New Person","email":"%s@company.com","department":"Finance","designation":"Analyst","salary":60000,"managerId":1}""";
    private static final String UPDATE_KARAN = """
            {"name":"Karan Shah","email":"karan.shah@company.com","department":"Finance","designation":"Accountant","managerId":1,"status":"ACTIVE"}""";

    private record Rule(String method, String path, String body, int allowedStatus, String access) {
    }

    private static final List<Rule> MATRIX = List.of(
            //        method   path                              body                    allowed admin hana manoj asha ravi
            new Rule("GET",    "/api/employees",                 null,                   200, "Y     Y    Y     Y    Y"),
            new Rule("GET",    "/api/employees/4",               null,                   200, "Y     Y    Y     Y    -"),
            new Rule("GET",    "/api/employees/team",            null,                   200, "-     -    Y     -    -"),
            new Rule("GET",    "/api/employees/4/salary",        null,                   200, "Y     Y    -     Y    -"),
            new Rule("PUT",    "/api/employees/7/salary",        "{\"salary\":81000}",   200, "Y     Y    -     -    -"),
            new Rule("PUT",    "/api/employees/2/salary",        "{\"salary\":141000}",  200, "Y     -    -     -    -"),
            new Rule("POST",   "/api/employees",                 NEW_EMPLOYEE,           201, "Y     Y    -     -    -"),
            new Rule("PUT",    "/api/employees/7",               UPDATE_KARAN,           200, "Y     Y    -     -    -"),
            new Rule("DELETE", "/api/employees/999",             null,                   404, "Y     -    -     -    -"),
            new Rule("GET",    "/api/admin/users",               null,                   200, "Y     -    -     -    -"));

    static Stream<Arguments> everyRoleOnEveryEndpoint() {
        List<Arguments> cases = new ArrayList<>();
        for (Rule rule : MATRIX) {
            String[] cells = rule.access().trim().split("\\s+");
            for (int i = 0; i < USERS.size(); i++) {
                cases.add(Arguments.of(USERS.get(i), rule.method(), rule.path(), rule.body(),
                        cells[i].equals("Y") ? rule.allowedStatus() : 403));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> everyEndpoint() {
        return MATRIX.stream().map(rule -> Arguments.of(rule.method(), rule.path(), rule.body()));
    }

    private static MockHttpServletRequestBuilder build(String method, String path, String body) {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body.formatted(UUID.randomUUID()));  // unique e-mail
        }
        return request;
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {4}")
    @MethodSource("everyRoleOnEveryEndpoint")
    void accessMatchesTheMatrix(String user, String method, String path, String body, int expected) throws Exception {
        mockMvc.perform(build(method, path, body).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "no token {0} {1} -> 401")
    @MethodSource("everyEndpoint")
    void withoutAToken_everyEndpointGives401(String method, String path, String body) throws Exception {
        mockMvc.perform(build(method, path, body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }
}
