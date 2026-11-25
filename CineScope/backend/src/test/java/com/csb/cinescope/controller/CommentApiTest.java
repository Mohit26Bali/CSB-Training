package com.csb.cinescope.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.TmdbFixtures;
import com.csb.cinescope.entity.Comment;
import com.csb.cinescope.entity.CommentStatus;
import com.csb.cinescope.entity.CommentVote;
import com.csb.cinescope.entity.Sentiment;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.service.CommentProcessor;

/**
 * Reviews and discussion through the real stack with only TMDB replaced: writing, replying, rating, editing, deleting, helpful
 * marks, sorting and paging, the processing pipeline's effect (masking, holding, sentiment), the rules (one rating per title,
 * no replies to replies, rate limit, duplicates), the numbers of the discussion summary, moderation by administrators, and what
 * happens to comments when an account is deleted. TMDB's rating for the fixture film (550) is 8.4.
 */
class CommentApiTest extends AbstractApiTest {

    private static final String TITLE = "/api/titles/movie/550";

    @Override
    protected boolean signedInByDefault() {
        return false; // every test signs in as the users it needs
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        MockHttpServletRequestBuilder builder = request.header("Authorization", token);
        return json == null ? mvc.perform(builder) : mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String comment(String body, Integer rating) {
        return "{\"body\":\"" + body + "\"" + (rating == null ? "" : ",\"rating\":" + rating) + "}";
    }

    private void filmExists() {
        tmdbReturns("/movie/550", TmdbFixtures.MOVIE_DETAIL); // one request; later lookups of the title come from the cache
    }

    /** Writes a comment straight into the database (no pipeline, no rate limit), for tests that need many. */
    private Comment seed(String username, String body, Integer rating, Sentiment sentiment, double score, CommentStatus status, Comment parent) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return commentRepository.save(new Comment(user, 550, "movie", parent, body, rating, sentiment, score, status, "Decision|PASSED|Published"));
    }

    private long idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        int start = json.indexOf("\"id\":") + 5;
        int end = start;
        while (Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    // ---- who can reach it ----

    @Test
    void everyCommentEndpointNeedsALogin() throws Exception {
        mvc.perform(get(TITLE + "/comments")).andExpect(status().isUnauthorized());
        mvc.perform(get(TITLE + "/discussion")).andExpect(status().isUnauthorized());
        mvc.perform(post(TITLE + "/comments").contentType(MediaType.APPLICATION_JSON).content(comment("Great film", null))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/comments/1/replies").contentType(MediaType.APPLICATION_JSON).content(comment("Agreed", null))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/comments/1").contentType(MediaType.APPLICATION_JSON).content(comment("Edited", null))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/comments/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/comments/1/helpful")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/comments")).andExpect(status().isUnauthorized());
    }

    // ---- writing ----

    @Test
    void aCommentWithARatingIsPublishedProcessedAndShownInTheList() throws Exception {
        String asha = register("asha");
        filmExists();

        send(post(TITLE + "/comments"), asha, comment("Absolutely brilliant film", 9))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Absolutely brilliant film"))
                .andExpect(jsonPath("$.rating").value(9))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.sentimentScore").value(0.5)) // "absolutely" strengthens "brilliant": 2 * 1.5 = 3, and 3 / (3 + 3)
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.helpfulCount").value(0))
                .andExpect(jsonPath("$.authorUsername").value("asha"))
                .andExpect(jsonPath("$.authorName").value("asha Display"))
                .andExpect(jsonPath("$.steps[*].name", contains("Cleaned", "Spam check", "Profanity filter", "Sentiment analysis", "Decision")))
                .andExpect(jsonPath("$.replies").isEmpty());

        send(get(TITLE + "/comments"), asha, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Absolutely brilliant film"));
        tmdb.verify();
    }

    @Test
    void theTextIsCleanedAndMildBadLanguageMasked() throws Exception {
        String asha = register("asha");
        filmExists();

        send(post(TITLE + "/comments"), asha, comment("  that   was damn good  ", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("that was d*** good"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.steps[0].outcome").value("CHANGED"))
                .andExpect(jsonPath("$.steps[2].outcome").value("CHANGED"));
    }

    @Test
    void strongLanguageAndLinkSpamAreHeldAndOnlyTheAuthorSeesThemInTheList() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        filmExists();

        send(post(TITLE + "/comments"), asha, comment("What a fucking awful ending", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.body").value("What a f****** awful ending"))
                .andExpect(jsonPath("$.steps[4].outcome").value("HELD"));
        send(post(TITLE + "/comments"), asha, comment("Free http://a.example http://b.example http://c.example", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        send(get(TITLE + "/comments"), asha, null).andExpect(jsonPath("$.totalResults").value(2)); // the author sees their held comments
        send(get(TITLE + "/comments"), ravi, null).andExpect(jsonPath("$.totalResults").value(0)); // nobody else does
        send(get(TITLE + "/discussion"), ravi, null).andExpect(jsonPath("$.commentCount").value(0));
    }

    @Test
    void badInputIsRefused() throws Exception {
        String asha = register("asha");
        filmExists();

        send(post(TITLE + "/comments"), asha, comment("", null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.body").exists());
        send(post(TITLE + "/comments"), asha, comment("x", null)).andExpect(status().isBadRequest());
        send(post(TITLE + "/comments"), asha, comment("y".repeat(1001), null)).andExpect(status().isBadRequest());
        send(post(TITLE + "/comments"), asha, comment("A fine comment", 0)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").value("rating must be between 1 and 10"));
        send(post(TITLE + "/comments"), asha, comment("A fine comment", 11)).andExpect(status().isBadRequest());
        send(post("/api/titles/book/550/comments"), asha, comment("A fine comment", null)).andExpect(status().isBadRequest());
        send(post("/api/titles/movie/0/comments"), asha, comment("A fine comment", null)).andExpect(status().isBadRequest());
        send(get(TITLE + "/comments?sort=zzz"), asha, null).andExpect(status().isBadRequest());
        send(get(TITLE + "/comments?page=0"), asha, null).andExpect(status().isBadRequest());
        send(get(TITLE + "/comments?size=31"), asha, null).andExpect(status().isBadRequest());
        assertEquals(0, commentRepository.count(), "nothing was stored");
    }

    @Test
    void aTitleThatDoesNotExistAtTmdbCannotBeCommentedOn() throws Exception {
        String asha = register("asha");
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/999"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        send(post("/api/titles/movie/999/comments"), asha, comment("Does this exist?", null)).andExpect(status().isNotFound());

        assertEquals(0, commentRepository.count());
    }

    @Test
    void aPersonCanRateATitleOnlyOnce_butCanCommentAsOftenAsTheyLike() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        filmExists();

        send(post(TITLE + "/comments"), asha, comment("First review", 8)).andExpect(status().isCreated());
        send(post(TITLE + "/comments"), asha, comment("Second review with another rating", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already rated this title. Edit your review to change the rating"));
        send(post(TITLE + "/comments"), asha, comment("A comment without a rating", null)).andExpect(status().isCreated());
        send(post(TITLE + "/comments"), ravi, comment("My own rating", 6)).andExpect(status().isCreated()); // another person may rate
    }

    @Test
    void postingTooFastIs429AndIdenticalTextIs409() throws Exception {
        String asha = register("asha");
        filmExists();

        send(post(TITLE + "/comments"), asha, comment("Same words twice", null)).andExpect(status().isCreated());
        send(post(TITLE + "/comments"), asha, comment("Same words twice", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already posted exactly this a moment ago"));
        for (int i = 2; i <= 5; i++) {
            send(post(TITLE + "/comments"), asha, comment("Comment number " + i, null)).andExpect(status().isCreated());
        }
        send(post(TITLE + "/comments"), asha, comment("One comment too many", null))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("You're posting too fast. Please wait a minute and try again"));
    }

    // ---- replies ----

    @Test
    void repliesAreNestedUnderTheirCommentOldestFirstAndOneLevelDeep() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        filmExists();
        long parent = idOf(send(post(TITLE + "/comments"), asha, comment("Loved the ending", 9)));

        long first = idOf(send(post("/api/comments/" + parent + "/replies"), ravi, comment("Same here", null)).andExpect(status().isCreated()).andExpect(jsonPath("$.parentId").value(parent)));
        send(post("/api/comments/" + parent + "/replies"), asha, comment("Glad you liked it too", null)).andExpect(status().isCreated());

        send(get(TITLE + "/comments"), ravi, null)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].replies[*].body", contains("Same here", "Glad you liked it too")))
                .andExpect(jsonPath("$.items[0].replies[0].mine").value(true))
                .andExpect(jsonPath("$.items[0].replies[1].mine").value(false));

        send(post("/api/comments/" + first + "/replies"), asha, comment("Replying to a reply", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can reply to a comment, but not to a reply"));
        send(post("/api/comments/" + parent + "/replies"), ravi, comment("A reply with a rating", 7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only a review can carry a rating, not a reply"));
        send(post("/api/comments/999999/replies"), ravi, comment("To nobody", null)).andExpect(status().isNotFound());
    }

    @Test
    void youCannotReplyToACommentYouCannotSee() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        filmExists();
        long held = idOf(send(post(TITLE + "/comments"), asha, comment("This is shit honestly", null)));

        send(post("/api/comments/" + held + "/replies"), ravi, comment("Replying to a held comment", null)).andExpect(status().isNotFound());
    }

    // ---- editing and deleting ----

    @Test
    void theAuthorCanEditAndTheTextIsProcessedAgain() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        filmExists();
        long id = idOf(send(post(TITLE + "/comments"), asha, comment("Boring and slow", 3)));

        send(put("/api/comments/" + id), asha, comment("On a second watch it is brilliant", 9))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("On a second watch it is brilliant"))
                .andExpect(jsonPath("$.rating").value(9))
                .andExpect(jsonPath("$.sentiment").value("POSITIVE"));

        send(put("/api/comments/" + id), ravi, comment("Hijacked", null)).andExpect(status().isForbidden());
        send(put("/api/comments/999999"), asha, comment("Nothing here", null)).andExpect(status().isNotFound());
        assertEquals("On a second watch it is brilliant", commentRepository.findById(id).orElseThrow().getBody());
    }

    @Test
    void editingCanHoldAComment_andFixingItPublishesAgain() throws Exception {
        String asha = register("asha");
        filmExists();
        long id = idOf(send(post(TITLE + "/comments"), asha, comment("Not bad at all", null)));

        send(put("/api/comments/" + id), asha, comment("Now it is fucking terrible", null)).andExpect(jsonPath("$.status").value("PENDING"));
        send(put("/api/comments/" + id), asha, comment("Now it is simply terrible", null)).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void aReplyCannotGetARatingByBeingEdited() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        filmExists();
        long parent = idOf(send(post(TITLE + "/comments"), asha, comment("Great film", null)));
        long reply = idOf(send(post("/api/comments/" + parent + "/replies"), ravi, comment("Agreed", null)));

        send(put("/api/comments/" + reply), ravi, comment("Agreed fully", 8)).andExpect(status().isBadRequest());
    }

    @Test
    void deletingAComment_takesItsRepliesAndMarksWithIt_andOnlyTheAuthorOrAnAdministratorMay() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        String admin = registerAdmin("boss");
        filmExists();
        long parent = idOf(send(post(TITLE + "/comments"), asha, comment("Worth discussing", 8)));
        send(post("/api/comments/" + parent + "/replies"), ravi, comment("Yes it is", null)).andExpect(status().isCreated());
        send(post("/api/comments/" + parent + "/helpful"), ravi, null).andExpect(status().isOk());

        send(delete("/api/comments/" + parent), ravi, null).andExpect(status().isForbidden());
        assertEquals(2, commentRepository.count());

        send(delete("/api/comments/" + parent), asha, null).andExpect(status().isNoContent());

        assertEquals(0, commentRepository.count(), "the reply went with it");
        assertEquals(0, commentVoteRepository.count(), "so did the helpful mark");
        send(delete("/api/comments/" + parent), asha, null).andExpect(status().isNotFound());

        long other = idOf(send(post(TITLE + "/comments"), ravi, comment("Another thought entirely", null)));
        send(delete("/api/comments/" + other), admin, null).andExpect(status().isNoContent()); // an administrator may delete any
    }

    // ---- helpful marks and sorting ----

    @Test
    void helpfulIsMarkedOncePerPersonCanBeTakenBackAndNotOnYourOwnComment() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        String meera = register("meera");
        filmExists();
        long id = idOf(send(post(TITLE + "/comments"), asha, comment("A very useful observation", null)));

        send(post("/api/comments/" + id + "/helpful"), ravi, null).andExpect(jsonPath("$.helpful").value(true)).andExpect(jsonPath("$.helpfulCount").value(1));
        send(post("/api/comments/" + id + "/helpful"), meera, null).andExpect(jsonPath("$.helpfulCount").value(2));
        send(get(TITLE + "/comments"), ravi, null).andExpect(jsonPath("$.items[0].helpfulCount").value(2)).andExpect(jsonPath("$.items[0].helpfulByMe").value(true));
        send(get(TITLE + "/comments"), asha, null).andExpect(jsonPath("$.items[0].helpfulByMe").value(false));

        send(post("/api/comments/" + id + "/helpful"), ravi, null).andExpect(jsonPath("$.helpful").value(false)).andExpect(jsonPath("$.helpfulCount").value(1));
        send(post("/api/comments/" + id + "/helpful"), asha, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can't mark your own comment as helpful"));
        assertEquals(1, commentVoteRepository.count());
    }

    @Test
    void theListCanBeSortedByHelpfulNewestMostPositiveAndMostCritical() throws Exception {
        register("asha");
        String viewer = register("viewer");
        User asha = userRepository.findByUsername("asha").orElseThrow();
        Comment calm = seed("asha", "Calm remark", null, Sentiment.NEUTRAL, 0.0, CommentStatus.PUBLISHED, null);
        calm.backdate(LocalDateTime.now().minusHours(3));
        Comment loud = seed("asha", "Loud praise", null, Sentiment.POSITIVE, 0.6, CommentStatus.PUBLISHED, null);
        loud.backdate(LocalDateTime.now().minusHours(2));
        Comment harsh = seed("asha", "Harsh words", null, Sentiment.NEGATIVE, -0.5, CommentStatus.PUBLISHED, null);
        harsh.backdate(LocalDateTime.now().minusHours(1));
        harsh.setHelpfulCount(5);
        calm.setHelpfulCount(2);
        commentRepository.saveAll(java.util.List.of(calm, loud, harsh));
        assertEquals("asha", asha.getUsername());

        send(get(TITLE + "/comments?sort=helpful"), viewer, null).andExpect(jsonPath("$.items[*].body", contains("Harsh words", "Calm remark", "Loud praise")));
        send(get(TITLE + "/comments?sort=newest"), viewer, null).andExpect(jsonPath("$.items[*].body", contains("Harsh words", "Loud praise", "Calm remark")));
        send(get(TITLE + "/comments?sort=positive"), viewer, null).andExpect(jsonPath("$.items[*].body", contains("Loud praise", "Calm remark", "Harsh words")));
        send(get(TITLE + "/comments?sort=critical"), viewer, null).andExpect(jsonPath("$.items[*].body", contains("Harsh words", "Calm remark", "Loud praise")));
    }

    @Test
    void longListsArePaged() throws Exception {
        register("asha");
        String viewer = register("viewer");
        for (int i = 1; i <= 7; i++) {
            seed("asha", "Comment " + i, null, Sentiment.NEUTRAL, 0.0, CommentStatus.PUBLISHED, null);
        }

        send(get(TITLE + "/comments?sort=newest&size=3"), viewer, null)
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.totalResults").value(7))
                .andExpect(jsonPath("$.totalPages").value(3));
        send(get(TITLE + "/comments?sort=newest&size=3&page=3"), viewer, null).andExpect(jsonPath("$.items", hasSize(1)));
    }

    // ---- the numbers ----

    @Test
    void theSummaryAddsUpRatingsMoodAndTopicsOfPublishedCommentsOnly() throws Exception {
        register("asha");
        register("ravi");
        register("meera");
        String viewer = register("viewer");
        Comment first = seed("asha", "The ending and the music were brilliant", 9, Sentiment.POSITIVE, 0.4, CommentStatus.PUBLISHED, null);
        seed("ravi", "Loved the music but the ending dragged", 7, Sentiment.NEUTRAL, 0.0, CommentStatus.PUBLISHED, null);
        seed("meera", "Boring ending and weak music", 5, Sentiment.NEGATIVE, -0.4, CommentStatus.PUBLISHED, null);
        seed("ravi", "Agree about the ending", null, Sentiment.NEUTRAL, 0.0, CommentStatus.PUBLISHED, first);
        seed("meera", "Held comment with a rating of one", 1, Sentiment.NEGATIVE, -0.9, CommentStatus.PENDING, null);
        filmExists();

        send(get(TITLE + "/discussion"), viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.replyCount").value(1))
                .andExpect(jsonPath("$.ratingCount").value(3)) // the held comment's 1 star is not counted
                .andExpect(jsonPath("$.averageRating").value(7.0))
                .andExpect(jsonPath("$.histogram", contains(0, 0, 0, 0, 1, 0, 1, 0, 1, 0)))
                .andExpect(jsonPath("$.tmdbRating").value(8.4))
                .andExpect(jsonPath("$.communityScore").value(7.9)) // (9 + 7 + 5 + 5 * 8.4) / (3 + 5)
                .andExpect(jsonPath("$.sentiment.positive").value(1))
                .andExpect(jsonPath("$.sentiment.neutral").value(2))
                .andExpect(jsonPath("$.sentiment.negative").value(1))
                .andExpect(jsonPath("$.sentiment.positivePercent").value(25))
                .andExpect(jsonPath("$.sentiment.neutralPercent").value(50))
                .andExpect(jsonPath("$.sentiment.negativePercent").value(25))
                .andExpect(jsonPath("$.topics[0].word").value("ending"))
                .andExpect(jsonPath("$.topics[0].count").value(4))
                .andExpect(jsonPath("$.topics[1].word").value("music"))
                .andExpect(jsonPath("$.topics[1].count").value(3));
    }

    @Test
    void withNoRatingsTheCommunityScoreIsTmdbsAndWithNoCommentsEverythingIsEmpty() throws Exception {
        String viewer = register("viewer");
        filmExists();

        send(get(TITLE + "/discussion"), viewer, null)
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist())
                .andExpect(jsonPath("$.communityScore").value(8.4))
                .andExpect(jsonPath("$.histogram", hasSize(10)))
                .andExpect(jsonPath("$.sentiment.positivePercent").value(0))
                .andExpect(jsonPath("$.topics", hasSize(0)));
    }

    @Test
    void ifTmdbIsDownTheDiscussionStillWorksWithoutItsRating() throws Exception {
        register("asha");
        String viewer = register("viewer");
        seed("asha", "A solid remark", 8, Sentiment.POSITIVE, 0.3, CommentStatus.PUBLISHED, null);
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/550"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        send(get(TITLE + "/discussion"), viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tmdbRating").doesNotExist())
                .andExpect(jsonPath("$.averageRating").value(8.0))
                .andExpect(jsonPath("$.communityScore").value(8.0));
    }

    // ---- moderation ----

    @Test
    void anAdministratorReviewsHeldCommentsAndTheDecisionTakesEffect() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        String admin = registerAdmin("boss");
        filmExists();
        long held = idOf(send(post(TITLE + "/comments"), asha, comment("This ending is shit but the acting is brilliant", 6)));
        long spam = idOf(send(post(TITLE + "/comments"), asha, comment("Buy http://a.example http://b.example http://c.example", null)));

        send(get("/api/admin/comments"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(2))
                .andExpect(jsonPath("$.items[*].authorUsername", contains("asha", "asha")))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        send(put("/api/admin/comments/" + held + "/moderation"), admin, "{\"action\":\"approve\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.steps[-1:].name").value("Moderator review"))
                .andExpect(jsonPath("$.steps[-1:].detail").value("Approved by boss"));
        send(put("/api/admin/comments/" + spam + "/moderation"), admin, "{\"action\":\"REJECT\"}").andExpect(jsonPath("$.status").value("REJECTED"));

        send(get(TITLE + "/comments"), ravi, null).andExpect(jsonPath("$.items[*].id", contains((int) held))); // approved: everyone sees it; rejected: nobody
        send(get(TITLE + "/comments"), asha, null)
                .andExpect(jsonPath("$.totalResults").value(2)) // the author still sees the rejected one, marked as such
                .andExpect(jsonPath("$.items[?(@.status == 'REJECTED')].id", contains((int) spam)));
        send(get("/api/admin/comments"), admin, null).andExpect(jsonPath("$.totalResults").value(0));
        send(get("/api/admin/comments?status=REJECTED"), admin, null).andExpect(jsonPath("$.items[*].id", contains((int) spam)));
        send(get(TITLE + "/discussion"), ravi, null).andExpect(jsonPath("$.commentCount").value(1)).andExpect(jsonPath("$.ratingCount").value(1));
    }

    @Test
    void moderationIsForAdministratorsOnlyAndRefusesNonsense() throws Exception {
        String asha = register("asha");
        String admin = registerAdmin("boss");
        filmExists();
        long held = idOf(send(post(TITLE + "/comments"), asha, comment("Pure shit", null)));

        send(get("/api/admin/comments"), asha, null).andExpect(status().isForbidden());
        send(put("/api/admin/comments/" + held + "/moderation"), asha, "{\"action\":\"APPROVE\"}").andExpect(status().isForbidden());
        assertEquals(CommentStatus.PENDING, commentRepository.findById(held).orElseThrow().getStatus());

        send(put("/api/admin/comments/" + held + "/moderation"), admin, "{\"action\":\"DELETE\"}").andExpect(status().isBadRequest());
        send(put("/api/admin/comments/" + held + "/moderation"), admin, "{}").andExpect(status().isBadRequest());
        send(put("/api/admin/comments/999999/moderation"), admin, "{\"action\":\"APPROVE\"}").andExpect(status().isNotFound());
        send(get("/api/admin/comments?status=NOPE"), admin, null).andExpect(status().isBadRequest());
    }

    // ---- accounts ----

    @Test
    void deletingAnAccountRemovesItsCommentsRepliesToThemAndItsMarksButNotOthersComments() throws Exception {
        String asha = register("asha");
        String ravi = register("ravi");
        String admin = registerAdmin("boss");
        filmExists();
        long ashas = idOf(send(post(TITLE + "/comments"), asha, comment("Asha's review", 8)));
        send(post("/api/comments/" + ashas + "/replies"), ravi, comment("Ravi answers Asha", null)).andExpect(status().isCreated());
        long ravis = idOf(send(post(TITLE + "/comments"), ravi, comment("Ravi's own review", 6)));
        send(post("/api/comments/" + ravis + "/replies"), asha, comment("Asha answers Ravi", null)).andExpect(status().isCreated());
        send(post("/api/comments/" + ravis + "/helpful"), asha, null).andExpect(status().isOk()); // asha's mark on ravi's comment
        send(post("/api/comments/" + ashas + "/helpful"), ravi, null).andExpect(status().isOk()); // ravi's mark on asha's comment
        assertEquals(4, commentRepository.count());

        long ashaId = userRepository.findByUsername("asha").orElseThrow().getId();
        send(delete("/api/admin/users/" + ashaId), admin, null).andExpect(status().isNoContent());

        assertFalse(userRepository.findByUsername("asha").isPresent());
        assertEquals(1, commentRepository.count(), "only ravi's own review is left");
        assertEquals("Ravi's own review", commentRepository.findAll().get(0).getBody());
        assertEquals(0, commentVoteRepository.count(), "marks given or received by asha are gone");
        send(get(TITLE + "/comments"), ravi, null)
                .andExpect(jsonPath("$.items[*].body", containsInAnyOrder("Ravi's own review")))
                .andExpect(jsonPath("$.items[0].replies", hasSize(0)))
                .andExpect(jsonPath("$.items[0].helpfulCount").value(0));
    }

    @Test
    void deletingYourOwnAccountAlsoRemovesYourComments() throws Exception {
        String asha = register("asha");
        filmExists();
        send(post(TITLE + "/comments"), asha, comment("A review that will disappear", 7)).andExpect(status().isCreated());

        send(delete("/api/profile"), asha, "{\"password\":\"Secret-Pass-1!\"}").andExpect(status().isNoContent());

        assertEquals(0, commentRepository.count());
        assertTrue(userRepository.findByUsername("asha").isEmpty());
    }

    @Test
    void stepsAreStoredAndComeBackDecoded() throws Exception {
        String asha = register("asha");
        filmExists();
        long id = idOf(send(post(TITLE + "/comments"), asha, comment("Not good, really boring", null)));

        String stored = commentRepository.findById(id).orElseThrow().getProcessing();
        assertEquals(5, CommentProcessor.decode(stored).size());
        send(get(TITLE + "/comments"), asha, null)
                .andExpect(jsonPath("$.items[0].sentiment").value("NEGATIVE"))
                .andExpect(jsonPath("$.items[0].steps[3].name").value("Sentiment analysis"))
                .andExpect(jsonPath("$.items[0].steps[3].detail").value(Matchers.startsWith("Negative (-")));
    }
}
