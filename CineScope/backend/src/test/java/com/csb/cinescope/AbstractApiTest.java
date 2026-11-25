package com.csb.cinescope;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;

/**
 * Shared set-up of every end-to-end API test: the whole application (real controllers, services, mapper, security chain,
 * caching gateway, database) with only TMDB replaced by a mock server ({@link FakeTmdbConfig}) and the database replaced by
 * in-memory H2 (profile "test", see src/test/resources/application-test.properties).
 *
 * <p>Before every test the caches are emptied, the TMDB mock is reset and all users and watchlist items are deleted, so each
 * test starts from the same blank state. The whole API except the sign-in endpoints needs a login now, so by default every
 * request of a test carries the token of a user called "viewer" (created fresh for each test); a request that sets its own
 * Authorization header, such as {@link #register}'s result, overrides it. A test class about anonymous behaviour or about
 * the exact set of users returns false from {@link #signedInByDefault()}. {@link #register} creates a user through the real endpoint and returns the
 * {@code Authorization} header value to use for that user.
 */
// app.seed.demo-data=false: a developer may switch demo data on in their local config/application.properties; tests must not care
@SpringBootTest(properties = { "tmdb.read-token=" + FakeTmdbConfig.TOKEN, "tmdb.base-url=" + FakeTmdbConfig.BASE, "app.seed.demo-data=false" })
@ActiveProfiles("test")
@Import(FakeTmdbConfig.class)
public abstract class AbstractApiTest {

    @Autowired protected WebApplicationContext context;
    @Autowired protected CacheManager cacheManager;
    @Autowired protected MockRestServiceServer tmdb;
    @Autowired protected UserRepository userRepository;
    @Autowired protected WatchlistItemRepository watchlistRepository;
    @Autowired protected com.csb.cinescope.repository.CommentVoteRepository commentVoteRepository;
    @Autowired protected com.csb.cinescope.repository.CommentRepository commentRepository;

    protected MockMvc mvc;

    @BeforeEach
    void resetState() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        tmdb.reset();
        commentVoteRepository.deleteAll(); // comments and their votes reference users, so they go first
        commentRepository.deleteAllInBatch();
        watchlistRepository.deleteAll();
        userRepository.deleteAll();
        if (signedInByDefault()) {
            String viewer = register("viewer");
            mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                    .defaultRequest(get("/").header("Authorization", viewer)).build();
        }
    }

    /** A MockMvc whose requests carry no token at all, for tests about what a visitor can and cannot reach. */
    protected MockMvc anonymous() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Whether requests carry a login token unless they set their own; see the class comment. */
    protected boolean signedInByDefault() {
        return true;
    }

    /** Expects exactly one TMDB request whose URL contains {@code urlPart}, and answers it with {@code json}. */
    protected void tmdbReturns(String urlPart, String json) {
        tmdb.expect(once(), requestTo(Matchers.containsString(urlPart)))
                .andExpect(header("Authorization", "Bearer " + FakeTmdbConfig.TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    /** Signs in through the API and returns the value for the Authorization header. */
    protected String login(String username, String password) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        int start = json.indexOf("\"token\":\"") + 9;
        return "Bearer " + json.substring(start, json.indexOf('"', start));
    }

    /**
     * Makes an administrator: registers the user, promotes the account directly in the database (the first administrator
     * cannot be made through the API) and signs in again, because promoting ends the sessions that existed before.
     */
    protected String registerAdmin(String username) throws Exception {
        register(username);
        com.csb.cinescope.entity.User user = userRepository.findByUsername(username).orElseThrow();
        user.changeRole(com.csb.cinescope.entity.Role.ADMIN);
        userRepository.save(user);
        return login(username, "Secret-Pass-1!");
    }

    /** Registers a user through the API (password "Secret-Pass-1!") and returns the value for the Authorization header. */
    protected String register(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\","
                + "\"password\":\"Secret-Pass-1!\",\"displayName\":\"" + username + " Display\"}";
        String json = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        int start = json.indexOf("\"token\":\"") + 9;
        return "Bearer " + json.substring(start, json.indexOf('"', start));
    }
}
