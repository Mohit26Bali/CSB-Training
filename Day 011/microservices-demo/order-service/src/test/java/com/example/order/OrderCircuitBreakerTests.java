package com.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
@AutoConfigureMockMvc
class OrderCircuitBreakerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CircuitBreakerRegistry registry;

    // The only product-service instance is down
    @DynamicPropertySource
    static void products(DynamicPropertyRegistry registry) throws IOException {
        String dead = StubServer.deadUrl();
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", () -> dead);
    }

    @Test
    void failuresReturnFallbackAndOpenTheCircuit() throws Exception {
        CircuitBreaker breaker = registry.circuitBreaker("productService");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // minimumNumberOfCalls = 5, all failing -> failure rate 100% > 50%
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.product.name").value("Product temporarily unavailable"))
                    .andExpect(jsonPath("$.product.price").value(0.0));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // While OPEN the call is not attempted at all; the fallback still answers
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1}"))
                .andExpect(jsonPath("$.product.name").value("Product temporarily unavailable"));
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }
}
