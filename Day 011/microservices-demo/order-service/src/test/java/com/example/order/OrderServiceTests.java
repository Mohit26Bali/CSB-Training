package com.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class OrderServiceTests {

    static StubServer instanceA;
    static StubServer instanceB;

    @Autowired
    private MockMvc mvc;

    // Two product-service instances under one service ID, without Eureka
    @DynamicPropertySource
    static void products(DynamicPropertyRegistry registry) throws IOException {
        instanceA = new StubServer(exchange -> StubServer.product(exchange, "A"));
        instanceB = new StubServer(exchange -> StubServer.product(exchange, "B"));
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", instanceA::url);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[1].uri", instanceB::url);
    }

    @AfterAll
    static void stop() {
        instanceA.stop();
        instanceB.stop();
    }

    private String createOrder(String body) throws Exception {
        return mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createOrderCallsProductServiceByLogicalName() throws Exception {
        String json = createOrder("{\"productId\":1,\"quantity\":2}");
        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("CREATED");
        assertThat((String) JsonPath.read(json, "$.product.name")).isEqualTo("Laptop");
        assertThat((Double) JsonPath.read(json, "$.total")).isEqualTo(150000.0);
    }

    @Test
    void loadBalancerUsesBothInstances() throws Exception {
        Set<String> instances = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            instances.add(JsonPath.read(createOrder("{\"productId\":1}"), "$.product.instance"));
        }
        assertThat(instances).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void createdOrderCanBeRetrieved() throws Exception {
        Integer id = JsonPath.read(createOrder("{\"productId\":3}"), "$.id");
        mvc.perform(get("/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(3));
        mvc.perform(get("/orders")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/orders/999")).andExpect(status().isNotFound());
    }
}
