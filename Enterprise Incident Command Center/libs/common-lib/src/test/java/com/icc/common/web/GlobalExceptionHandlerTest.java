package com.icc.common.web;

import com.icc.common.correlation.CorrelationIds;
import com.icc.common.error.ApiException;
import com.icc.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void apiException_usesItsCodeAndMessage() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Only a priority 1 incident can be escalated"))
                .andExpect(jsonPath("$.path").value("/test/conflict"));
    }

    @Test
    void accessDenied_becomes403() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unreadableBody_becomes400() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unexpectedError_becomes500_andHidesTheRealMessage() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void serviceUnavailable_becomes503() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("The service is temporarily unavailable"));
    }

    @Test
    void connectionRefusedToAService_becomes503_notA500() throws Exception {
        mockMvc.perform(get("/test/refused"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void wrongTypeOfPathVariable_becomes400() throws Exception {
        mockMvc.perform(get("/test/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void correlationId_comesFromMdc() throws Exception {
        MDC.put(CorrelationIds.MDC_KEY, "abc-123");

        mockMvc.perform(get("/test/conflict"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @RestController
    public static class TestController {

        @GetMapping("/test/conflict")
        public String conflict() {
            throw new ApiException(ErrorCode.CONFLICT, "Only a priority 1 incident can be escalated");
        }

        @GetMapping("/test/denied")
        public String denied() {
            throw new AccessDeniedException("not allowed");
        }

        @GetMapping("/test/unavailable")
        public String unavailable() {
            // the same way the gateway's load balancer filter throws it
            throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to find instance for incident-service");
        }

        @GetMapping("/test/refused")
        public String refused() {
            // how the gateway's routing client reports a service that is registered but not running
            throw new ResourceAccessException("I/O error: Connection refused");
        }

        @GetMapping("/test/number/{n}")
        public String number(@org.springframework.web.bind.annotation.PathVariable("n") int n) {
            return "ok";
        }

        @GetMapping("/test/boom")
        public String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @PostMapping("/test/body")
        public String body(@RequestBody Payload payload) {
            return "ok";
        }

        public record Payload(String name) {
        }
    }
}
