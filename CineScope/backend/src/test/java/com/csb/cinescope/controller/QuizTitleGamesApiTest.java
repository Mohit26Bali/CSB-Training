package com.csb.cinescope.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.QuizQuestion;
import com.csb.cinescope.repository.QuizQuestionRepository;
import com.csb.cinescope.repository.QuizRoundRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The cast game ("which film or show has these actors?") and the poster game ("name the blurred poster") through the real stack, with
 * TMDB replaced by a fake that answers any title: titles whose id is a multiple of seven have no poster and no cast (so they must be
 * skipped), all others have a poster at /poster-{type}-{id}.jpg and actors named "Actor {type}-{id} {n}". The questions come from the
 * titles of the seeded quotes. The speed bonus is checked by moving the recorded "shown" time in the database.
 */
class QuizTitleGamesApiTest extends AbstractApiTest {

    private static final Pattern ACTOR = Pattern.compile("Actor (movie|tv)-(\\d+) \\d");

    @Autowired QuizQuestionRepository questions;
    @Autowired QuizRoundRepository rounds;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    protected boolean signedInByDefault() {
        return false;
    }

    /** Every TMDB title request is answered; the id decides what the title has. */
    private void tmdbHasEveryTitle() {
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/3/"))).andRespond(request -> {
            Matcher m = Pattern.compile("/(movie|tv)/(\\d+)").matcher(request.getURI().getPath());
            if (!m.find()) {
                return withServerError().createResponse(request);
            }
            String type = m.group(1);
            long id = Long.parseLong(m.group(2));
            boolean bare = id % 7 == 0;
            StringBuilder cast = new StringBuilder();
            for (int k = 1; k <= 5 && !bare; k++) {
                cast.append(k > 1 ? "," : "").append("{\"id\":").append(k).append(",\"name\":\"Actor ").append(type).append('-').append(id).append(' ').append(k).append("\",\"character\":\"c\"}");
            }
            String named = type.equals("movie") ? "\"title\":\"T" + id + "\",\"release_date\":\"2000-01-01\"" : "\"name\":\"T" + id + "\",\"first_air_date\":\"2000-01-01\"";
            String body = "{\"id\":" + id + "," + named + (bare ? "" : ",\"poster_path\":\"/poster-" + type + "-" + id + ".jpg\"") + ",\"credits\":{\"cast\":[" + cast + "]}}";
            return withSuccess(body, MediaType.APPLICATION_JSON).createResponse(request);
        });
    }

    private ResultActions start(String token, String category) throws Exception {
        return mvc.perform(post("/api/quiz/rounds").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"" + category + "\"}"));
    }

    private JsonNode started(String token, String category) throws Exception {
        return json.readTree(start(token, category).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions answer(String token, long round, int seq, int choice) throws Exception {
        return mvc.perform(post("/api/quiz/rounds/" + round + "/answers").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"seq\":" + seq + ",\"choice\":" + choice + "}"));
    }

    private ResultActions shown(String token, long round, int seq) throws Exception {
        return mvc.perform(post("/api/quiz/rounds/" + round + "/questions/" + seq + "/shown").header("Authorization", token));
    }

    private int right(long round, int seq) {
        return questions.findByRoundIdAndSeq(round, seq).orElseThrow().getCorrectIndex();
    }

    private int wrong(long round, int seq) {
        return (right(round, seq) + 1) % 4;
    }

    private void setShownSecondsAgo(long round, int seq, long seconds) {
        setShownMillisAgo(round, seq, seconds * 1000);
    }

    private void setShownMillisAgo(long round, int seq, long millis) {
        jdbc.update("update quiz_questions set shown_at = ? where round_id = ? and seq = ?", Timestamp.valueOf(LocalDateTime.now().minusNanos(millis * 1_000_000)), round, seq);
    }

    @Test
    void aCastRoundShowsThreeActorsAndFourTitlesAndNeverTheAnswer() throws Exception {
        String token = register("cast1");
        tmdbHasEveryTitle();

        String body = start(token, "CAST").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode round = json.readTree(body);

        assertThat(round.get("category").asText()).isEqualTo("CAST");
        assertThat(round.get("questions")).hasSize(10);
        for (JsonNode q : round.get("questions")) {
            assertThat(q.get("kind").asText()).isEqualTo("CAST");
            assertThat(q.get("actors")).hasSize(3);
            assertThat(q.get("options")).hasSize(4);
            assertThat(q.get("line").isNull()).isTrue();
            assertThat(q.get("posterPath").isNull()).isTrue();
            Set<String> distinct = new HashSet<>();
            q.get("options").forEach(o -> distinct.add(o.asText().toLowerCase()));
            assertThat(distinct).hasSize(4);
        }
        assertThat(body).doesNotContain("correct").doesNotContain("Index");
    }

    @Test
    void theActorsShownBelongToTheTitleThatIsTheRightAnswer() throws Exception {
        String token = register("cast2");
        tmdbHasEveryTitle();
        JsonNode round = started(token, "CAST");

        for (JsonNode view : round.get("questions")) {
            QuizQuestion question = questions.findByRoundIdAndSeq(round.get("id").asLong(), view.get("seq").asInt()).orElseThrow();
            assertThat(question.getOptions().get(question.getCorrectIndex())).isEqualTo(question.getTitle());
            for (JsonNode actor : view.get("actors")) {
                Matcher m = ACTOR.matcher(actor.asText());
                assertThat(m.matches()).as(actor.asText()).isTrue();
                assertThat(m.group(1)).isEqualTo(question.getMediaType());
                assertThat(Long.parseLong(m.group(2))).isEqualTo(question.getTmdbId());
            }
        }
    }

    @Test
    void aPosterRoundShowsAPosterAndFourTitlesAndNeverTheAnswer() throws Exception {
        String token = register("poster1");
        tmdbHasEveryTitle();

        String body = start(token, "POSTER").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode round = json.readTree(body);

        assertThat(round.get("questions")).hasSize(10);
        for (JsonNode view : round.get("questions")) {
            QuizQuestion question = questions.findByRoundIdAndSeq(round.get("id").asLong(), view.get("seq").asInt()).orElseThrow();
            assertThat(view.get("kind").asText()).isEqualTo("POSTER");
            assertThat(view.get("posterPath").asText()).isEqualTo("/poster-" + question.getMediaType() + "-" + question.getTmdbId() + ".jpg");
            assertThat(view.get("actors").isNull()).isTrue();
            assertThat(view.get("options")).hasSize(4);
        }
        assertThat(body).doesNotContain("correct").doesNotContain("Index");
    }

    @Test
    void aRoundNeverUsesTheSameTitleTwiceAndSkipsTitlesWithNoCastOrPoster() throws Exception {
        String token = register("mix1");
        tmdbHasEveryTitle();
        for (String game : List.of("CAST", "POSTER", "CAST", "POSTER")) {
            long round = started(token, game).get("id").asLong();
            Set<String> titles = new HashSet<>();
            for (int seq = 1; seq <= 10; seq++) {
                QuizQuestion question = questions.findByRoundIdAndSeq(round, seq).orElseThrow();
                assertThat(titles.add(question.getMediaType() + ":" + question.getTmdbId())).as("title repeated in " + game).isTrue();
                assertThat(question.getTmdbId() % 7).as("a title with no cast or poster was used").isNotZero();
            }
        }
    }

    @Test
    void ifTmdbCannotSupplyEnoughTitlesTheAnswerIsATryAgainErrorAndNoRoundIsCreated() throws Exception {
        String token = register("down1");
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/3/"))).andRespond(withServerError());

        start(token, "CAST").andExpect(status().isBadGateway()).andExpect(jsonPath("$.message", Matchers.containsString("try again")));

        assertThat(rounds.count()).isZero();
        assertThat(questions.count()).isZero();
    }

    @Test
    void theAnswerRevealsTheTitleAndNoSpeaker() throws Exception {
        String token = register("reveal1");
        tmdbHasEveryTitle();
        long round = started(token, "CAST").get("id").asLong();
        QuizQuestion question = questions.findByRoundIdAndSeq(round, 1).orElseThrow();

        answer(token, round, 1, right(round, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reveal.title").value(question.getTitle()))
                .andExpect(jsonPath("$.reveal.tmdbId").value(question.getTmdbId()))
                .andExpect(jsonPath("$.reveal.mediaType").value(question.getMediaType()))
                .andExpect(jsonPath("$.reveal.year").value(question.getReleaseYear()))
                .andExpect(jsonPath("$.reveal.speaker").doesNotExist())
                .andExpect(jsonPath("$.speedBonus").value(0));
    }

    @Test
    void aPerfectCastRoundScoresTheSameAsTheQuoteQuizAndEarnsNoSpeedBonus() throws Exception {
        String token = register("cast3");
        tmdbHasEveryTitle();
        long round = started(token, "CAST").get("id").asLong();

        for (int seq = 1; seq <= 9; seq++) {
            shown(token, round, seq).andExpect(status().isNoContent()); // reported shown, but the cast game has no speed bonus
            answer(token, round, seq, right(round, seq)).andExpect(jsonPath("$.speedBonus").value(0));
        }
        answer(token, round, 10, right(round, 10)).andExpect(jsonPath("$.score").value(170)).andExpect(jsonPath("$.finished").value(true));
    }

    @Test
    void aQuickRightAnswerInThePosterGameEarnsASpeedBonusMeasuredOnTheServer() throws Exception {
        String token = register("poster2");
        tmdbHasEveryTitle();
        long round = started(token, "POSTER").get("id").asLong();

        shown(token, round, 1).andExpect(status().isNoContent());
        answer(token, round, 1, right(round, 1)).andExpect(jsonPath("$.speedBonus").value(5)).andExpect(jsonPath("$.points").value(15)).andExpect(jsonPath("$.score").value(15));

        // never reported on screen: no clock, no bonus
        answer(token, round, 2, right(round, 2)).andExpect(jsonPath("$.speedBonus").value(0)).andExpect(jsonPath("$.points").value(12));

        shown(token, round, 3).andExpect(status().isNoContent());
        setShownSecondsAgo(round, 3, 8); // a little slower: the smaller bonus
        answer(token, round, 3, right(round, 3)).andExpect(jsonPath("$.speedBonus").value(2)).andExpect(jsonPath("$.points").value(16)); // 10 + 4 for the streak + 2

        shown(token, round, 4).andExpect(status().isNoContent());
        setShownSecondsAgo(round, 4, 20); // too slow
        answer(token, round, 4, right(round, 4)).andExpect(jsonPath("$.speedBonus").value(0)).andExpect(jsonPath("$.points").value(16)); // 10 + 6 for the streak
    }

    @Test
    void theLimitsAreExactNotRoundedDownToWholeSeconds() throws Exception {
        String token = register("poster8");
        tmdbHasEveryTitle();
        long round = started(token, "POSTER").get("id").asLong();

        shown(token, round, 1).andExpect(status().isNoContent());
        setShownMillisAgo(round, 1, 5_500); // inside the fast limit of 6 seconds
        answer(token, round, 1, right(round, 1)).andExpect(jsonPath("$.speedBonus").value(5));

        shown(token, round, 2).andExpect(status().isNoContent());
        setShownMillisAgo(round, 2, 6_500); // just past 6 seconds: the smaller bonus, not the big one
        answer(token, round, 2, right(round, 2)).andExpect(jsonPath("$.speedBonus").value(2));

        shown(token, round, 3).andExpect(status().isNoContent());
        setShownMillisAgo(round, 3, 12_900); // 12.9 seconds is not "within 12"
        answer(token, round, 3, right(round, 3)).andExpect(jsonPath("$.speedBonus").value(0));
    }

    @Test
    void aWrongAnswerEarnsNoBonusEvenWhenFast() throws Exception {
        String token = register("poster3");
        tmdbHasEveryTitle();
        long round = started(token, "POSTER").get("id").asLong();

        shown(token, round, 1).andExpect(status().isNoContent());
        answer(token, round, 1, wrong(round, 1)).andExpect(jsonPath("$.correct").value(false)).andExpect(jsonPath("$.speedBonus").value(0)).andExpect(jsonPath("$.points").value(0));
    }

    @Test
    void reportingAQuestionShownTwiceCannotRestartTheClock() throws Exception {
        String token = register("poster4");
        tmdbHasEveryTitle();
        long round = started(token, "POSTER").get("id").asLong();

        shown(token, round, 1).andExpect(status().isNoContent());
        setShownSecondsAgo(round, 1, 20);
        shown(token, round, 1).andExpect(status().isNoContent()); // a second report must not reset it to now

        answer(token, round, 1, right(round, 1)).andExpect(jsonPath("$.speedBonus").value(0));
    }

    @Test
    void youCannotReportAnotherPlayersQuestionAndTheQuoteQuizIgnoresTheReport() throws Exception {
        String owner = register("poster5");
        String other = register("poster6");
        tmdbHasEveryTitle();
        long poster = started(owner, "POSTER").get("id").asLong();

        shown(other, poster, 1).andExpect(status().isNotFound());
        shown(owner, poster, 11).andExpect(status().isNotFound());
        shown(owner, poster + 999, 1).andExpect(status().isNotFound());

        long quotes = started(owner, "ANIME").get("id").asLong();
        shown(owner, quotes, 1).andExpect(status().isNoContent());
        assertThat(questions.findByRoundIdAndSeq(quotes, 1).orElseThrow().getShownAt()).isNull();
    }

    @Test
    void anAnsweredQuestionIsNotMarkedShownAfterwards() throws Exception {
        String token = register("poster7");
        tmdbHasEveryTitle();
        long round = started(token, "POSTER").get("id").asLong();
        answer(token, round, 1, right(round, 1)).andExpect(status().isOk());

        shown(token, round, 1).andExpect(status().isNoContent());

        assertThat(questions.findByRoundIdAndSeq(round, 1).orElseThrow().getShownAt()).isNull();
    }

    @Test
    void eachGameHasItsOwnLeaderboard() throws Exception {
        String token = register("board1");
        tmdbHasEveryTitle();
        long round = started(token, "POSTER").get("id").asLong();
        for (int seq = 1; seq <= 10; seq++) {
            answer(token, round, seq, right(round, seq)).andExpect(status().isOk());
        }

        mvc.perform(get("/api/quiz/leaderboard").param("category", "POSTER").header("Authorization", token)).andExpect(jsonPath("$.entries.length()").value(1)).andExpect(jsonPath("$.entries[0].score").value(170));
        mvc.perform(get("/api/quiz/leaderboard").param("category", "CAST").header("Authorization", token)).andExpect(jsonPath("$.entries.length()").value(0));
        mvc.perform(get("/api/quiz/leaderboard").param("category", "ANIME").header("Authorization", token)).andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void theNewGamesNeedALogin() throws Exception {
        anonymous().perform(post("/api/quiz/rounds").contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"CAST\"}")).andExpect(status().isUnauthorized());
        anonymous().perform(post("/api/quiz/rounds/1/questions/1/shown")).andExpect(status().isUnauthorized());
    }
}
