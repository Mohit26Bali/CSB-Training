package com.csb.cinescope.config;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.csb.cinescope.FakeTmdbConfig;
import com.csb.cinescope.config.DemoDataInitializer.DemoUser;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.security.PasswordPolicy;

/**
 * The demo accounts and watchlists of {@link DemoDataInitializer}, with the seeder switched on. It uses its own in-memory
 * database (so the other API tests, which empty the shared one before every test, cannot wipe the data) and, before every
 * test, empties the tables and seeds again, so each test starts from the same freshly seeded state. Everything is checked
 * the way a person would meet it: the accounts really sign in through the login endpoint, and the lists come back from the
 * watchlist endpoints.
 */
@SpringBootTest(properties = {
        "tmdb.read-token=" + FakeTmdbConfig.TOKEN,
        "tmdb.base-url=" + FakeTmdbConfig.BASE,
        "app.seed.demo-data=true",
        "spring.datasource.url=jdbc:h2:mem:cinescopeseed;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000" })
@ActiveProfiles("test")
@Import(FakeTmdbConfig.class)
class DemoDataInitializerTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired WatchlistItemRepository items;
    @Autowired DemoDataInitializer seeder;
    @Autowired PasswordEncoder encoder;
    @Autowired com.csb.cinescope.repository.CommentRepository commentRepository;
    @Autowired com.csb.cinescope.repository.CommentVoteRepository commentVotes;
    @Autowired org.springframework.test.web.client.MockRestServiceServer tmdb;
    @Autowired org.springframework.transaction.support.TransactionTemplate transaction;

    MockMvc mvc;

    @BeforeEach
    void seedFresh() {
        commentVotes.deleteAll();
        commentRepository.deleteAllInBatch();
        items.deleteAll();
        users.deleteAll();
        seeder.run(null);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String passwordOf(String username) {
        return DemoDataInitializer.USERS.stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow().password();
    }

    private String token(String username) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + passwordOf(username) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = json.indexOf("\"token\":\"") + 9;
        return "Bearer " + json.substring(start, json.indexOf('"', start));
    }

    private List<WatchlistItem> listOf(String username) {
        User user = users.findByUsername(username).orElseThrow();
        return items.findAll().stream().filter(i -> i.getUser().getId().equals(user.getId())).toList();
    }

    // ---- the accounts ----

    @Test
    void theSixDemoAccountsExistWithTheirDetailsAndBcryptHashesWithOwnSalts() {
        assertEquals(List.of("asha", "ravi", "meera", "sam", "karan", "admin"), DemoDataInitializer.USERS.stream().map(DemoUser::username).toList());
        Set<String> hashes = new HashSet<>();
        for (DemoUser demo : DemoDataInitializer.USERS) {
            User user = users.findByUsername(demo.username()).orElseThrow(() -> new AssertionError("missing " + demo.username()));
            assertEquals(demo.username() + "@example.com", user.getEmail());
            assertEquals(demo.displayName(), user.getDisplayName());
            assertEquals(demo.region(), user.getRegion());
            assertTrue(user.getPassword().startsWith("$2"), "stored as a BCrypt hash, never as text");
            assertFalse(user.getPassword().contains(demo.password()));
            assertTrue(encoder.matches(demo.password(), user.getPassword()));
            assertEquals(demo.role(), user.getRole());
            hashes.add(user.getPassword());
        }
        assertEquals(6, hashes.size(), "every account has its own salt, so no two hashes are equal");
        assertEquals("US", users.findByUsername("sam").orElseThrow().getRegion());
    }

    @Test
    void onlyTheDemoAdministratorIsAnAdministratorAndItsPasswordIsItsOwn() {
        assertEquals(List.of("admin"), users.findAll().stream().filter(u -> u.getRole() == com.csb.cinescope.entity.Role.ADMIN).map(User::getUsername).toList());
        User admin = users.findByUsername("admin").orElseThrow();
        assertFalse(encoder.matches(DemoDataInitializer.DEMO_PASSWORD, admin.getPassword()), "the shared demo password must not open the admin account");
        assertNotEquals(DemoDataInitializer.DEMO_PASSWORD, DemoDataInitializer.ADMIN_PASSWORD);
    }

    @Test
    void everyDemoAccountSignsInThroughTheRealLoginEndpoint() throws Exception {
        for (DemoUser demo : DemoDataInitializer.USERS) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + demo.username() + "\",\"password\":\"" + demo.password() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.username").value(demo.username()))
                    .andExpect(jsonPath("$.displayName").value(demo.displayName()))
                    .andExpect(jsonPath("$.region").value(demo.region()))
                    .andExpect(jsonPath("$.role").value(demo.role().name()));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"asha\",\"password\":\"Wrong-Pass-1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theDemoPasswordObeysTheSignUpRulesAndContainsNoUsername() {
        for (DemoUser demo : DemoDataInitializer.USERS) {
            assertNull(PasswordPolicy.problem(demo.password()), demo.username());
            assertTrue(demo.password().length() <= PasswordPolicy.MAX_LENGTH, demo.username());
            assertNull(PasswordPolicy.identityProblem(demo.password(), demo.username(), demo.username() + "@example.com"), demo.username());
        }
    }

    // ---- the watchlists ----

    @Test
    void theSummaryEndpointShowsTheSeededCountsPerStatus() throws Exception {
        Map<String, int[]> expected = Map.of( // total, want, watching, watched
                "asha", new int[] { 9, 3, 2, 4 },
                "ravi", new int[] { 6, 2, 1, 3 },
                "meera", new int[] { 5, 2, 1, 2 },
                "sam", new int[] { 5, 2, 0, 3 },
                "karan", new int[] { 0, 0, 0, 0 },
                "admin", new int[] { 0, 0, 0, 0 });
        for (Map.Entry<String, int[]> e : expected.entrySet()) {
            int[] n = e.getValue();
            mvc.perform(get("/api/watchlist/summary").header("Authorization", token(e.getKey())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(n[0]))
                    .andExpect(jsonPath("$.want").value(n[1]))
                    .andExpect(jsonPath("$.watching").value(n[2]))
                    .andExpect(jsonPath("$.watched").value(n[3]));
        }
    }

    @Test
    void theListEndpointReturnsTheTitlesWithTheirSnapshotRatingsAndNotes() throws Exception {
        String asha = token("asha");

        mvc.perform(get("/api/watchlist?status=WATCHED").header("Authorization", asha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(4))
                .andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Inception", "Interstellar", "The Dark Knight", "3 Idiots")))
                .andExpect(jsonPath("$.items[*].rating", containsInAnyOrder(9, 10, 9, 10)));

        mvc.perform(get("/api/watchlist/movie/157336").header("Authorization", asha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.year").value(2014))
                .andExpect(jsonPath("$.posterPath").value("/yQvGrMoipbRoddT0ZR8tPoR7NfX.jpg"))
                .andExpect(jsonPath("$.status").value("WATCHED"))
                .andExpect(jsonPath("$.rating").value(10))
                .andExpect(jsonPath("$.note").value("Cried at the docking scene."));

        mvc.perform(get("/api/watchlist?type=tv").header("Authorization", asha))
                .andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Breaking Bad", "Money Heist")));
        mvc.perform(get("/api/watchlist?sort=title").header("Authorization", asha))
                .andExpect(jsonPath("$.items[0].title").value("3 Idiots"));
    }

    @Test
    void usersNeverSeeEachOthersEntries() throws Exception {
        String ravi = token("ravi");

        mvc.perform(get("/api/watchlist/movie/27205").header("Authorization", ravi)).andExpect(status().isNotFound()); // asha's Inception
        mvc.perform(get("/api/watchlist/movie/550").header("Authorization", ravi)).andExpect(status().isOk()); // his own Fight Club
        mvc.perform(get("/api/watchlist?size=50").header("Authorization", ravi))
                .andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Fight Club", "The Godfather", "Pulp Fiction", "Game of Thrones", "The Matrix", "Chak De! India")));
    }

    @Test
    void aNewAccountHasAnEmptyList() throws Exception {
        mvc.perform(get("/api/watchlist").header("Authorization", token("karan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalResults").value(0));
    }

    @Test
    void everySeededRowFollowsTheRulesOfTheWatchlist() {
        int total = 0;
        for (DemoUser demo : DemoDataInitializer.USERS) {
            List<WatchlistItem> list = listOf(demo.username());
            assertEquals(demo.list().size(), list.size(), demo.username());
            Set<String> keys = new HashSet<>();
            for (WatchlistItem item : list) {
                assertTrue(keys.add(item.getMediaType() + "-" + item.getTmdbId()), "a title appears once per list: " + item.getTitle());
                assertTrue(item.getMediaType().equals("movie") || item.getMediaType().equals("tv"));
                assertTrue(item.getTitle() != null && !item.getTitle().isBlank());
                assertTrue(item.getPosterPath() != null && item.getPosterPath().startsWith("/"), item.getTitle());
                assertTrue(item.getReleaseYear() != null && item.getReleaseYear() >= 1900, item.getTitle());
                if (item.getRating() != null) {
                    assertEquals(WatchStatus.WATCHED, item.getStatus(), "a rating only goes with WATCHED: " + item.getTitle());
                    assertTrue(item.getRating() >= 1 && item.getRating() <= 10);
                }
                if (item.getStatus() == WatchStatus.WATCHED) {
                    assertTrue(item.getRating() != null, "the demo lists rate what they have watched: " + item.getTitle());
                }
                if (item.getNote() != null) {
                    assertTrue(item.getNote().length() <= 500);
                }
                total++;
            }
        }
        assertEquals(25, total);
        assertEquals(25, items.count());
    }

    @Test
    void everyTitleOfEveryDemoListIsKnownToTheSeeder() {
        for (DemoUser demo : DemoDataInitializer.USERS) {
            for (DemoDataInitializer.Entry entry : demo.list()) {
                assertTrue(DemoDataInitializer.nameOf(entry.type(), entry.tmdbId()) != null, entry.type() + " " + entry.tmdbId());
            }
        }
    }

    // ---- running it again ----

    @Test
    void startingAgainChangesNothing() {
        long usersBefore = users.count();
        long itemsBefore = items.count();
        String hashBefore = users.findByUsername("asha").orElseThrow().getPassword();

        seeder.run(null);
        seeder.run(null);

        assertEquals(usersBefore, users.count());
        assertEquals(itemsBefore, items.count());
        assertEquals(hashBefore, users.findByUsername("asha").orElseThrow().getPassword(), "an existing account is left alone");
    }

    @Test
    void whatSomeoneDeletedIsNotPutBackAndAnEditedListIsNotTouched() {
        List<WatchlistItem> asha = listOf("asha");
        items.delete(asha.get(0));
        User karan = users.findByUsername("karan").orElseThrow();
        WatchlistItem mine = new WatchlistItem(karan, 550, "movie", "Fight Club", "/jSziioSwPVrOy9Yow3XhWIBDjq1.jpg", 1999);
        items.save(mine);

        seeder.run(null);

        assertEquals(asha.size() - 1, listOf("asha").size(), "the deleted entry stays deleted");
        assertEquals(1, listOf("karan").size(), "karan's own list was not filled with demo titles");
        assertNotEquals(0, items.count());
    }

    @Test
    void anExistingAccountWithAnEmptyListGetsItsDemoListWithoutBeingRecreated() {
        User asha = users.findByUsername("asha").orElseThrow();
        items.deleteAll(listOf("asha"));
        assertEquals(0, listOf("asha").size());

        seeder.run(null);

        assertEquals(9, listOf("asha").size());
        assertEquals(asha.getId(), users.findByUsername("asha").orElseThrow().getId(), "same account, not a new one");
    }

    // ---- the demo discussion ----

    @Test
    void theDemoDiscussionIsWrittenThroughTheRealPipeline() {
        transaction.executeWithoutResult(status -> {
            List<com.csb.cinescope.entity.Comment> all = commentRepository.findAll();
            long replies = all.stream().filter(c -> c.getParent() != null).count();
    
            assertEquals(DemoDataInitializer.COMMENTS.size() + DemoDataInitializer.COMMENTS.stream().mapToInt(c -> c.replies().size()).sum(), all.size());
            assertEquals(17, all.size() - replies);
            assertEquals(5, replies);
            for (com.csb.cinescope.entity.Comment c : all) {
                assertEquals(5, com.csb.cinescope.service.CommentProcessor.decode(c.getProcessing()).size(), "every comment carries its five processing steps");
                assertTrue(c.getSentimentScore() > -1 && c.getSentimentScore() < 1);
                assertTrue(c.getRating() == null || c.getParent() == null, "only top-level comments carry a rating");
            }
        });
    }

    @Test
    void twoDemoCommentsAreHeldForModerationAndOneIsMasked() {
        transaction.executeWithoutResult(status -> {
            List<com.csb.cinescope.entity.Comment> held = commentRepository.findAll().stream().filter(c -> c.getStatus() == com.csb.cinescope.entity.CommentStatus.PENDING).toList();
            assertEquals(2, held.size());
            assertTrue(held.stream().allMatch(c -> c.getUser().getUsername().equals("karan") && c.getTmdbId() == 550));
            assertTrue(held.stream().anyMatch(c -> c.getBody().contains("s***")), "the strong word is masked even while held");
    
            com.csb.cinescope.entity.Comment masked = commentRepository.findAll().stream().filter(c -> c.getTmdbId() == 496243 && c.getUser().getUsername().equals("karan")).findFirst().orElseThrow();
            assertEquals(com.csb.cinescope.entity.CommentStatus.PUBLISHED, masked.getStatus(), "a mild word is masked but not held");
            assertTrue(masked.getBody().contains("s*****"), masked.getBody());
            assertFalse(masked.getBody().contains("stupid"));
        });
    }

    @Test
    void helpfulCountsMatchTheMarksAndNobodyMarksTheirOwnComment() {
        for (com.csb.cinescope.entity.Comment c : commentRepository.findAll()) {
            long marks = commentVotes.findAll().stream().filter(v -> v.getComment().getId().equals(c.getId())).count();
            assertEquals(marks, c.getHelpfulCount(), c.getBody());
            assertTrue(commentVotes.findAll().stream().noneMatch(v -> v.getComment().getId().equals(c.getId()) && v.getUser().getId().equals(c.getUser().getId())));
        }
        assertTrue(commentVotes.count() > 10);
    }

    @Test
    void thePublicDiscussionOfInceptionAddsUpAsWrittenInTheSeedData() throws Exception {
        tmdb.reset();
        tmdb.expect(manyTimes(), requestTo(containsString("/movie/27205")))
                .andRespond(withSuccess(com.csb.cinescope.TmdbFixtures.MOVIE_DETAIL, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/titles/movie/27205/discussion").header("Authorization", token("ravi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.replyCount").value(2))
                .andExpect(jsonPath("$.ratingCount").value(3))
                .andExpect(jsonPath("$.averageRating").value(7.0)) // 9, 7 and 5
                .andExpect(jsonPath("$.sentiment.positive").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.sentiment.negative").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.communityScore").isNotEmpty());

        mvc.perform(get("/api/titles/movie/27205/comments?sort=helpful").header("Authorization", token("ravi")))
                .andExpect(jsonPath("$.items[0].authorUsername").value("asha")) // three marks beat the others
                .andExpect(jsonPath("$.items[0].helpfulCount").value(3))
                .andExpect(jsonPath("$.items[0].helpfulByMe").value(true))
                .andExpect(jsonPath("$.items[0].replies[0].authorUsername").value("ravi"))
                .andExpect(jsonPath("$.items[0].replies[0].mine").value(true));
    }

    @Test
    void theModerationQueueHoldsTheTwoFlaggedCommentsAndOnlyForTheAdministrator() throws Exception {
        mvc.perform(get("/api/admin/comments").header("Authorization", token("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(2))
                .andExpect(jsonPath("$.items[*].authorUsername", containsInAnyOrder("karan", "karan")))
                .andExpect(jsonPath("$.items[*].status", containsInAnyOrder("PENDING", "PENDING")));
        mvc.perform(get("/api/admin/comments").header("Authorization", token("asha"))).andExpect(status().isForbidden());

        mvc.perform(get("/api/titles/movie/550/comments").header("Authorization", token("karan")))
                .andExpect(jsonPath("$.items[?(@.status == 'PENDING')]").isNotEmpty()); // karan can see his own held comments...
        mvc.perform(get("/api/titles/movie/550/comments").header("Authorization", token("asha")))
                .andExpect(jsonPath("$.items[?(@.status == 'PENDING')]").isEmpty()); // ...nobody else can
    }

    @Test
    void startingAgainDoesNotWriteTheDiscussionTwice() {
        long before = commentRepository.count();
        long marksBefore = commentVotes.count();

        seeder.run(null);

        assertEquals(before, commentRepository.count());
        assertEquals(marksBefore, commentVotes.count());
    }
}
