package com.example.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@TestPropertySource(properties = "app.instance=8082")
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsProductWithInstance() throws Exception {
        mvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.price").value(75000))
                .andExpect(jsonPath("$.instance").value("8082"));
    }

    @Test
    void unknownIdGetsGenericProduct() throws Exception {
        mvc.perform(get("/products/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Product 9"));
    }
}
