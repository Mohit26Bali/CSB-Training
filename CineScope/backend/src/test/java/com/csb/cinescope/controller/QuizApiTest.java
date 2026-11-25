package com.csb.cinescope.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.entity.QuizQuestion;
import com.csb.cinescope.repository.QuizQuestionRepository;
import com.csb.cinescope.repository.QuizRoundRepository;
import com.csb.cinescope.repository.QuoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The quiz through the real stack: starting a round, what the player is and is not told, answering, scoring and streaks, the rules
 * (one answer per question, only your own round), finishing, and the leaderboard. The seeded quotes are used; the right answer
 * is read from the database, which is what the server itself knows and never sends before an answer.
 */
class QuizApiTest extends AbstractApiTest {

    @Autowired QuizQuestionRepository questions;
    @Autowired QuizRoundRepository rounds;
    @Autowired QuoteRepository quotes;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    protected boolean signedInByDefault() {
        return false; // each test signs in as the players it needs
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

    private int rightChoice(long round, int seq) {
        return questions.findByRoundIdAndSeq(round, seq).orElseThrow().getCorrectIndex();
    }

    private int wrongChoice(long round, int seq) {
        return (rightChoice(round, seq) + 1) % 4;
    }

    /** Plays a whole round; {@code rightAnswers[i]} says whether question i+1 is answered correctly. Returns the last answer's reply. */
    private JsonNode play(String token, String category, boolean... rightAnswers) throws Exception {
        long round = started(token, category).get("id").asLong();
        JsonNode last = null;
        for (int i = 0; i < 10; i++) {
            int choice = rightAnswers[i] ? rightChoice(round, i + 1) : wrongChoice(round, i + 1);
            last = json.readTree(answer(token, round, i + 1, choice).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        }
        return last;
    }

    private static boolean[] all(boolean value) {
        boolean[] answers = new boolean[10];
        java.util.Arrays.fill(answers, value);
        return answers;
    }

    @Test
    void aRoundHasTenQuestionsOfFourDistinctOptionsAndNeverRevealsTheAnswer() throws Exception {
        String token = register("player1");

        String body = start(token, "ANIME").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode round = json.readTree(body);

        assertThat(round.get("category").asText()).isEqualTo("ANIME");
        assertThat(round.get("total").asInt()).isEqualTo(10);
        assertThat(round.get("questions")).hasSize(10);
        for (JsonNode q : round.get("questions")) {
            assertThat(q.get("options")).hasSize(4);
            Set<String> distinct = new HashSet<>();
            q.get("options").forEach(o -> distinct.add(o.asText().toLowerCase()));
            assertThat(distinct).as("options of question " + q.get("seq")).hasSize(4);
            assertThat(q.get("line").asText()).isNotBlank();
            assertThat(q.get("prompt").asText()).isNotBlank();
        }
        assertThat(body).as("the right answer must not be in the reply").doesNotContain("correct").doesNotContain("Index");
    }

    @Test
    void theQuestionsComeFromTheChosenCategoryAndTheRightOptionIsThere() throws Exception {
        String token = register("player2");
        JsonNode round = started(token, "BOLLYWOOD");

        for (JsonNode view : round.get("questions")) {
            QuizQuestion question = questions.findByRoundIdAndSeq(round.get("id").asLong(), view.get("seq").asInt()).orElseThrow();
            Quote quote = quotes.findById(question.getQuoteId()).orElseThrow();
            assertThat(quote.getCategory().name()).isEqualTo("BOLLYWOOD");
            assertThat(view.get("line").asText()).isEqualTo(quote.getLine());
            String right = view.get("kind").asText().equals("TITLE") ? quote.getTitle() : quote.getSpeaker();
            assertThat(question.getOptions().get(question.getCorrectIndex())).isEqualTo(right);
            assertThat(question.getOptions().stream().filter(o -> o.equalsIgnoreCase(right))).hasSize(1);
        }
    }

    @Test
    void aMixedRoundUsesSeveralCategories() throws Exception {
        String token = register("player3");
        JsonNode round = started(token, "ALL");
        Set<String> categories = new HashSet<>();
        for (JsonNode view : round.get("questions")) {
            Quote quote = quotes.findById(questions.findByRoundIdAndSeq(round.get("id").asLong(), view.get("seq").asInt()).orElseThrow().getQuoteId()).orElseThrow();
            categories.add(quote.getCategory().name());
        }
        assertThat(categories.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aRightAnswerEarnsPointsGrowingWithTheStreakAndAWrongOneEndsIt() throws Exception {
        String token = register("player4");
        long round = started(token, "TV").get("id").asLong();

        answer(token, round, 1, rightChoice(round, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true)).andExpect(jsonPath("$.points").value(10)).andExpect(jsonPath("$.streak").value(1)).andExpect(jsonPath("$.score").value(10));
        answer(token, round, 2, rightChoice(round, 2)).andExpect(jsonPath("$.points").value(12)).andExpect(jsonPath("$.streak").value(2)).andExpect(jsonPath("$.score").value(22));
        answer(token, round, 3, rightChoice(round, 3)).andExpect(jsonPath("$.points").value(14)).andExpect(jsonPath("$.score").value(36));
        answer(token, round, 4, wrongChoice(round, 4)).andExpect(jsonPath("$.correct").value(false)).andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.streak").value(0)).andExpect(jsonPath("$.bestStreak").value(3)).andExpect(jsonPath("$.score").value(36))
                .andExpect(jsonPath("$.correctIndex").value(rightChoice(round, 4)));
        answer(token, round, 5, rightChoice(round, 5)).andExpect(jsonPath("$.points").value(10)).andExpect(jsonPath("$.streak").value(1)).andExpect(jsonPath("$.score").value(46));
    }

    @Test
    void theAnswerRevealsWhereTheQuoteIsFrom() throws Exception {
        String token = register("player5");
        long round = started(token, "HOLLYWOOD").get("id").asLong();
        Quote quote = quotes.findById(questions.findByRoundIdAndSeq(round, 1).orElseThrow().getQuoteId()).orElseThrow();

        answer(token, round, 1, rightChoice(round, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reveal.title").value(quote.getTitle()))
                .andExpect(jsonPath("$.reveal.speaker").value(quote.getSpeaker()))
                .andExpect(jsonPath("$.reveal.year").value(quote.getReleaseYear()))
                .andExpect(jsonPath("$.reveal.tmdbId").value(quote.getTmdbId()))
                .andExpect(jsonPath("$.reveal.mediaType").value(quote.getMediaType()));
    }

    @Test
    void aQuestionCanBeAnsweredOnlyOnce() throws Exception {
        String token = register("player6");
        long round = started(token, "TV").get("id").asLong();
        answer(token, round, 1, wrongChoice(round, 1)).andExpect(status().isOk());

        answer(token, round, 1, rightChoice(round, 1)).andExpect(status().isConflict());
    }

    @Test
    void badInputIsRefused() throws Exception {
        String token = register("player7");
        long round = started(token, "TV").get("id").asLong();

        start(token, "MUSIC").andExpect(status().isBadRequest());
        start(token, "").andExpect(status().isBadRequest());
        answer(token, round, 1, 4).andExpect(status().isBadRequest());
        answer(token, round, 1, -1).andExpect(status().isBadRequest());
        answer(token, round, 0, 1).andExpect(status().isBadRequest());
        answer(token, round, 11, 1).andExpect(status().isNotFound());
        answer(token, round + 999, 1, 1).andExpect(status().isNotFound());
    }

    @Test
    void youCannotAnswerSomeoneElsesRound() throws Exception {
        String owner = register("player8");
        String other = register("player9");
        long round = started(owner, "TV").get("id").asLong();

        answer(other, round, 1, rightChoice(round, 1)).andExpect(status().isNotFound());
        assertThat(questions.findByRoundIdAndSeq(round, 1).orElseThrow().isAnswered()).isFalse();
    }

    @Test
    void theQuizNeedsALogin() throws Exception {
        anonymous().perform(post("/api/quiz/rounds").contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"TV\"}")).andExpect(status().isUnauthorized());
        anonymous().perform(get("/api/quiz/leaderboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void theLastAnswerFinishesTheRoundAndAPerfectGameScoresOneHundredSeventy() throws Exception {
        String token = register("player10");
        long round = started(token, "HOLLYWOOD").get("id").asLong();

        for (int i = 1; i <= 9; i++) {
            answer(token, round, i, rightChoice(round, i)).andExpect(jsonPath("$.finished").value(false)).andExpect(jsonPath("$.answered").value(i));
        }
        // 10 + 12 + 14 + 16 + 18 + 20 + 20 + 20 + 20 + 20 = 170
        answer(token, round, 10, rightChoice(round, 10)).andExpect(jsonPath("$.finished").value(true)).andExpect(jsonPath("$.score").value(170))
                .andExpect(jsonPath("$.correctCount").value(10)).andExpect(jsonPath("$.bestStreak").value(10));
        assertThat(rounds.findById(round).orElseThrow().getFinishedAt()).isNotNull();
    }

    @Test
    void anUnfinishedRoundDoesNotCountOnTheLeaderboard() throws Exception {
        String token = register("player11");
        long round = started(token, "ANIME").get("id").asLong();
        answer(token, round, 1, rightChoice(round, 1)).andExpect(status().isOk());

        mvc.perform(get("/api/quiz/leaderboard").param("category", "ANIME").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(0)).andExpect(jsonPath("$.you").doesNotExist());
    }

    @Test
    void theLeaderboardRanksBestScoresAndTiesShareAPlace() throws Exception {
        String ann = register("ann");
        String bob = register("bob");
        String cyd = register("cyd");
        String dee = register("dee");
        play(ann, "ANIME", all(true));   // 170
        play(bob, "ANIME", all(false));  // 0
        play(cyd, "ANIME", all(true));    // 170, same as ann
        play(dee, "ANIME", new boolean[] { true, true, true, true, true, false, false, false, false, false }); // 10+12+14+16+18 = 70
        play(bob, "ANIME", new boolean[] { true, false, false, false, false, false, false, false, false, false }); // bob improves to 10

        mvc.perform(get("/api/quiz/leaderboard").param("category", "ANIME").header("Authorization", dee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ANIME"))
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].username").value("ann")).andExpect(jsonPath("$.entries[0].rank").value(1)).andExpect(jsonPath("$.entries[0].score").value(170))
                .andExpect(jsonPath("$.entries[1].username").value("cyd")).andExpect(jsonPath("$.entries[1].rank").value(1)) // a tie shares first place
                .andExpect(jsonPath("$.entries[2].username").value("dee")).andExpect(jsonPath("$.entries[2].rank").value(3)).andExpect(jsonPath("$.entries[2].you").value(true))
                .andExpect(jsonPath("$.entries[3].username").value("bob")).andExpect(jsonPath("$.entries[3].score").value(10)) // best round, not the latest or the first
                .andExpect(jsonPath("$.you.rank").value(3)).andExpect(jsonPath("$.you.score").value(70));
    }

    @Test
    void eachCategoryHasItsOwnBoard() throws Exception {
        String token = register("player12");
        play(token, "TV", all(true));

        mvc.perform(get("/api/quiz/leaderboard").param("category", "TV").header("Authorization", token)).andExpect(jsonPath("$.entries.length()").value(1));
        mvc.perform(get("/api/quiz/leaderboard").param("category", "ANIME").header("Authorization", token)).andExpect(jsonPath("$.entries.length()").value(0));
        mvc.perform(get("/api/quiz/leaderboard").param("category", "NOPE").header("Authorization", token)).andExpect(status().isBadRequest());
    }

    @Test
    void theBoardShowsOnlyTheTopTenButStillTellsYouYourOwnPlace() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            tokens.add(register("crowd" + (char) ('a' + i)));
        }
        for (int i = 0; i < 10; i++) {
            play(tokens.get(i), "TV", all(true)); // ten players on 170
        }
        play(tokens.get(10), "TV", new boolean[] { true, false, false, false, false, false, false, false, false, false }); // the eleventh on 10

        mvc.perform(get("/api/quiz/leaderboard").param("category", "TV").header("Authorization", tokens.get(10)))
                .andExpect(jsonPath("$.entries.length()").value(10))
                .andExpect(jsonPath("$.entries[*].username", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("crowdk"))))
                .andExpect(jsonPath("$.you.username").value("crowdk")).andExpect(jsonPath("$.you.rank").value(11));
    }

    @Test
    void twoRoundsInARowDoNotRepeatAQuote() throws Exception {
        String token = register("player13");
        Set<Long> first = new HashSet<>();
        long one = started(token, "ANIME").get("id").asLong();
        for (int seq = 1; seq <= 10; seq++) {
            first.add(questions.findByRoundIdAndSeq(one, seq).orElseThrow().getQuoteId());
        }
        long two = started(token, "ANIME").get("id").asLong();
        for (int seq = 1; seq <= 10; seq++) {
            assertThat(first).doesNotContain(questions.findByRoundIdAndSeq(two, seq).orElseThrow().getQuoteId());
        }
    }

    @Test
    void deletingAnAccountRemovesItsRoundsAndQuestions() throws Exception {
        String token = register("player14");
        play(token, "TV", all(true));
        assertThat(rounds.count()).isEqualTo(1);

        userRepository.delete(userRepository.findByUsername("player14").orElseThrow());
        userRepository.flush();

        assertThat(rounds.count()).isZero();
        assertThat(questions.count()).isZero();
    }
}
