package com.company.secureapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** The full application on the MySQL test database; logs each demo user in once. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class TestSupport {

    static final Map<String, String> PASSWORDS = Map.of("admin", "Admin@123", "hana", "Hana@123",
            "manoj", "Manoj@123", "asha", "Asha@123", "ravi", "Ravi@123");

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    MockMvc mockMvc;

    String token(String user) throws Exception {
        if (!TOKENS.containsKey(user)) {
            String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, PASSWORDS.get(user))))
                    .andReturn().getResponse().getContentAsString();
            TOKENS.put(user, JsonPath.read(body, "$.accessToken"));
        }
        return "Bearer " + TOKENS.get(user);
    }
}
