package com.csb.banking.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.csb.banking.dto.ErrorResponse;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Describes the API for Swagger UI ({@code /swagger-ui.html}) and the OpenAPI document ({@code /v3/api-docs}).
 * springdoc generates the endpoint list by scanning the {@code controller/} classes; this class adds what cannot be
 * derived from the code:
 * <ul>
 *   <li>title, version and a short guide on how to authorize;</li>
 *   <li>the JWT bearer security scheme - after {@code POST /api/auth/login}, press "Authorize" in Swagger UI and
 *       paste the token, and every "Try it out" call is sent with it (public endpoints opt out with
 *       {@code @SecurityRequirements} in {@code controller/AuthController});</li>
 *   <li>the shared error body ({@code dto/ErrorResponse}) as the "default" response of every operation, since errors
 *       are produced centrally by {@code exception/GlobalExceptionHandler} and not declared per method.</li>
 * </ul>
 * The two docs URLs are open in {@code config/SecurityConfig}; switch them off in production with the
 * {@code SWAGGER_ENABLED=false} environment variable (see application.properties).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI bankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Crestline Bank API")
                        .version("v1")
                        .description("""
                                REST API of the Crestline Bank training project: accounts, deposits, withdrawals, \
                                transfers and transaction history.

                                **How to try it:** call `POST /api/auth/login` (for example `ravi` / `customer123`), \
                                copy the `token` from the response, press **Authorize** and paste it. Customers can use \
                                the account and money endpoints; `admin` / `admin12345` can use the `/api/admin` ones.

                                **Money operations** accept an optional `Idempotency-Key` header: a request repeated with \
                                the same key is executed once and answered with the first result."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class));
            if (resolved == null || openApi.getPaths() == null) {
                return;
            }
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);

            Schema<?> reference = new Schema<>().$ref("#/components/schemas/ErrorResponse");
            ApiResponse error = new ApiResponse()
                    .description("Error: 400 invalid input, 401 not logged in, 403 not allowed, 404 not found, "
                            + "409 conflict, 422 business rule violated, 429 too many login attempts")
                    .content(new Content().addMediaType("application/json", new MediaType().schema(reference)));
            openApi.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> operation.getResponses().addApiResponse("default", error)));
        };
    }
}
