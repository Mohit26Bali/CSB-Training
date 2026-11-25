package com.company.secureapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** The error contract (401/403 vs controller errors), correlation ids, DTOs and the salary audit. */
@ExtendWith(OutputCaptureExtension.class)
class ErrorContractAndDataTest extends TestSupport {

    @Test
    void missingToken_gives401_withTheFullContract_andTheCorrelationIdOfTheResponseHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/employees"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();
        String headerId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(headerId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"correlationId\":\"" + headerId + "\"");
    }

    @Test
    void clientCorrelationId_isKept() throws Exception {
        mockMvc.perform(get("/api/employees").header("X-Correlation-Id", "client-req-42"))
                .andExpect(header().string("X-Correlation-Id", "client-req-42"))
                .andExpect(jsonPath("$.correlationId").value("client-req-42"));
    }

    @Test
    void invalidAndTamperedTokens_give401_AUTH_INVALID_TOKEN() throws Exception {
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Token is malformed or invalid"));

        String real = token("asha").substring(7);
        String[] parts = real.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    /** Valid JWT without permission: 403, and one structured line in the security log. */
    @Test
    void validTokenWithoutPermission_gives403_andIsLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(put("/api/employees/7/salary").header(HttpHeaders.AUTHORIZATION, token("asha"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"salary\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.correlationId").exists());
        assertThat(output).contains("security_event=ACCESS_DENIED user=asha authorities=[ROLE_EMPLOYEE] method=PUT path=/api/employees/7/salary");
    }

    /** Controller errors use the same contract but their own codes, so clients can tell them apart. */
    @Test
    void controllerErrors_areDistinguishableBy_code() throws Exception {
        mockMvc.perform(post("/api/employees").header(HttpHeaders.AUTHORIZATION, token("hana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("email must be a well-formed email address")));
        mockMvc.perform(get("/api/employees/999").header(HttpHeaders.AUTHORIZATION, token("hana")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));
        mockMvc.perform(post("/api/employees").header(HttpHeaders.AUTHORIZATION, token("hana"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"X","email":"asha.verma@company.com","department":"D","designation":"E","salary":1}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"asha\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_BAD_CREDENTIALS"));
    }

    /** DTOs: the detail view never has a salary; only the salary endpoint shows it. */
    @Test
    void salaryAppearsOnlyInTheSalaryEndpoint() throws Exception {
        mockMvc.perform(get("/api/employees/4").header(HttpHeaders.AUTHORIZATION, token("hana")))
                .andExpect(jsonPath("$.email").value("asha.verma@company.com"))
                .andExpect(jsonPath("$.salary").doesNotExist());
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, token("asha")))
                .andExpect(jsonPath("$[0].salary").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
        mockMvc.perform(get("/api/employees/4/salary").header(HttpHeaders.AUTHORIZATION, token("asha")))
                .andExpect(jsonPath("$.salary").value(95000.0));
    }

    @Test
    void salaryChange_isAudited(CapturedOutput output) throws Exception {
        mockMvc.perform(put("/api/employees/6/salary").header(HttpHeaders.AUTHORIZATION, token("hana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"salary\":72000}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/6/salary/history").header(HttpHeaders.AUTHORIZATION, token("admin")))
                .andExpect(jsonPath("$[-1].oldSalary").value(70000.0))
                .andExpect(jsonPath("$[-1].newSalary").value(72000.0))
                .andExpect(jsonPath("$[-1].changedBy").value("hana"));
        assertThat(output).contains("audit_event=SALARY_CHANGED employee=6 old=70000.00 new=72000 by=hana");
    }

    @Test
    void managerTeam_isTheirDirectReports() throws Exception {
        mockMvc.perform(get("/api/employees/team").header(HttpHeaders.AUTHORIZATION, token("manoj")))
                .andExpect(jsonPath("$[*].name").value(Matchers.contains("Asha Verma", "Ravi Iyer")));
    }

    @Test
    void adminCreatesALogin() throws Exception {
        mockMvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"meena\",\"password\":\"Meena@123\",\"employeeId\":6,\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles").value(Matchers.contains("EMPLOYEE")));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"meena\",\"password\":\"Meena@123\"}"))
                .andExpect(status().isOk());
    }
}
