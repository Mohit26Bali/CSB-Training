package com.csb.cinescope.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Title, description and the login scheme of the API documentation. springdoc builds the endpoint list itself by scanning
 * {@code controller/}; Swagger UI is at {@code /swagger-ui.html}, the OpenAPI JSON at {@code /v3/api-docs}.
 *
 * <p>The bearer scheme makes the "Authorize" button appear: call {@code POST /api/auth/login}, paste the token, and the
 * protected calls (watchlist) can be tried. Public endpoints (the catalogue, register, login) opt out of the padlock with
 * {@code @SecurityRequirements} on their controller or method.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI cineScopeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CineScope API")
                        .version("v1")
                        .description("""
                                Movie and TV discovery backed by TMDB, with accounts and a personal watchlist.

                                **Try the protected endpoints:** call `POST /api/auth/register` (or `/login`), copy the `token` \
                                from the response, press **Authorize** and paste it.

                                This product uses the TMDB API but is not endorsed or certified by TMDB."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
