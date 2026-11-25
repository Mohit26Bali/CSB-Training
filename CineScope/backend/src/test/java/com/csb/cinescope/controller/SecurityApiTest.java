package com.csb.cinescope.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.TmdbFixtures;

/**
 * Which endpoints are public and which are not ("deny by default"), plus CORS and the diagnostics endpoint. Catches a
 * security rule that is accidentally widened or narrowed.
 */
class SecurityApiTest extends AbstractApiTest {

    @Override
    protected boolean signedInByDefault() {
        return false; // these tests are about what a visitor without a token may and may not reach
    }

    @Test
    void theWholeCatalogueNeedsALogin_justLikeAStreamingService() throws Exception {
        for (String path : new String[] { "/api/home", "/api/titles?category=popular&type=movie", "/api/titles/movie/550",
                "/api/titles/movie/550/providers", "/api/people/3001", "/api/discover?type=movie", "/api/search?query=nolan",
                "/api/genres?type=movie", "/api/tonight" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Please log in to continue"));
        }
        tmdb.verify(); // none of these reached TMDB: no expectation was set
    }

    @Test
    void aValidTokenOpensTheCatalogue() throws Exception {
        String token = register("asha");
        tmdbReturns("/genre/movie/list", TmdbFixtures.GENRES_MOVIE);

        mvc.perform(get("/api/genres?type=movie").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/genres?type=movie").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void theAdminUrlsAreClosedToVisitorsAndOrdinaryUsers() throws Exception {
        for (String path : new String[] { "/api/admin/users", "/api/admin/users/1", "/api/admin/anything-else" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        String user = register("asha");
        mvc.perform(get("/api/admin/users").header("Authorization", user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());
        // even a path that does not exist is the administrators' business under /api/admin/
        mvc.perform(get("/api/admin/anything-else").header("Authorization", user)).andExpect(status().isForbidden());
    }

    @Test
    void anythingNotExplicitlyPublicNeedsALogin() throws Exception {
        for (String path : new String[] { "/api/auth/me", "/api/watchlist", "/api/watchlist/summary", "/api/unknown", "/api/admin", "/actuator/env" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theDiagnosticsEndpointIsPublicAndNamesTheDatabaseKind() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("CineScope"))
                .andExpect(jsonPath("$.database").value("H2")); // the tests run on the in-memory database, never on MySQL
    }

    @Test
    void swaggerIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void theOpenApiDocumentDescribesTheLoginScheme() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/watchlist/{type}/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/profile/password'].put").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty()) // public: no padlock
                .andExpect(jsonPath("$.paths['/api/showcase'].get.security").isEmpty()) // the sign-in screens' data is public
                .andExpect(jsonPath("$.paths['/api/home'].get.security").doesNotExist()) // so it falls under the global requirement...
                .andExpect(jsonPath("$.security[0].bearerAuth").exists()); // ...which is the bearer token
    }

    @Test
    void corsAllowsTheConfiguredFrontendOriginOnly() throws Exception {
        mvc.perform(options("/api/watchlist/movie/550")
                        .header("Origin", "http://localhost:5180")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5180"));

        mvc.perform(options("/api/watchlist/movie/550")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }
}
