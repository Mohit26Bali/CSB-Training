package com.icc.common.feign;

import com.icc.common.web.GlobalExceptionHandler;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeignExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Both advices are registered, as in a real service: the Feign one must win over the catch-all.
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(), new FeignExceptionHandler())
                .build();
    }

    @Test
    void downstream404_becomes404() throws Exception {
        mockMvc.perform(get("/test/feign/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void downstream409_becomes409() throws Exception {
        mockMvc.perform(get("/test/feign/409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void downstream500_becomes503_notA500() throws Exception {
        mockMvc.perform(get("/test/feign/500"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void openCircuitBreaker_becomes503_notA500() throws Exception {
        mockMvc.perform(get("/test/open-circuit"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @RestController
    public static class TestController {

        @GetMapping("/test/open-circuit")
        public String openCircuit() {
            throw new NoFallbackAvailableException("No fallback available.", new IllegalStateException("open"));
        }

        @GetMapping("/test/feign/{status}")
        public String fail(@PathVariable("status") int status) {
            Request request = Request.create(Request.HttpMethod.GET, "http://other-service/x", Map.of(),
                    null, StandardCharsets.UTF_8, new RequestTemplate());
            Response response = Response.builder().status(status).reason("reason").request(request)
                    .headers(Map.of()).build();
            throw FeignException.errorStatus("OtherClient#call", response);
        }
    }
}
