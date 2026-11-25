package com.example.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registryEndpointIsUp() {
        assertThat(rest.getForEntity("/eureka/apps", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
