package com.csb.banking.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * The API documentation: the OpenAPI document and Swagger UI are reachable without a login, list every endpoint with
 * its description, declare the JWT scheme, and describe the shared error body. Guards against a controller change or a
 * security rule silently breaking the docs.
 */
class OpenApiDocsTest extends AbstractApiTest {

    @Test
    void openApiDocumentListsEveryEndpointWithoutLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Crestline Bank API"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.summary").value("Log in"))
                .andExpect(jsonPath("$.paths['/api/auth/register']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/me']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountNumber}/deposit']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountNumber}/withdraw']").exists())
                .andExpect(jsonPath("$.paths['/api/transfers']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountNumber}/transactions']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/accounts']").exists());
    }

    @Test
    void documentsAuthenticationFiltersIdempotencyAndErrors() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.paths['/api/transfers'].post.responses.default").exists())
                // history exposes the filters as query parameters
                .andExpect(jsonPath("$.paths['/api/accounts/{accountNumber}/transactions'].get.parameters[?(@.name=='minAmount')]").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountNumber}/transactions'].get.parameters[?(@.name=='status')]").exists())
                // money endpoints document the Idempotency-Key header
                .andExpect(jsonPath("$.paths['/api/transfers'].post.parameters[?(@.name=='Idempotency-Key')]").exists());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }
}
