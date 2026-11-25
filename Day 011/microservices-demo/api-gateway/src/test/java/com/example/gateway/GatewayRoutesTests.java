package com.example.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTests {

    static StubServer productService;
    static StubServer orderService;

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) throws IOException {
        productService = new StubServer(exchange -> StubServer.product(exchange, "8082"));
        orderService = new StubServer(exchange -> "{\"id\":1,\"status\":\"CREATED\"}");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", productService::url);
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", orderService::url);
    }

    @AfterAll
    static void stop() {
        productService.stop();
        orderService.stop();
    }

    @Test
    void productsRouteUsesLoadBalancedServiceAndAddsHeader() {
        client.get().uri("/products/1").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway", "api-gateway")
                .expectBody().jsonPath("$.name").isEqualTo("Laptop");
    }

    @Test
    void apiProductsPathIsRewritten() {
        client.get().uri("/api/products/2").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(2);
        assertThat(productService.requests).contains("GET /products/2 source=null");
    }

    @Test
    void ordersRouteAddsRequestHeader() {
        client.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"productId\":1}")
                .exchange().expectStatus().isOk();
        client.get().uri("/api/orders/1").exchange().expectStatus().isOk();
        assertThat(orderService.requests)
                .contains("POST /orders source=api-gateway", "GET /orders/1 source=api-gateway");
    }
}
