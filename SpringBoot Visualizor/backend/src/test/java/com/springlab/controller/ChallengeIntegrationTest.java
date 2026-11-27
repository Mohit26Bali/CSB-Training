package com.springlab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.springlab.entity.Attempt;
import com.springlab.entity.AttemptStatus;
import com.springlab.entity.Difficulty;
import com.springlab.entity.Question;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChallengeIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT = "jwt-security";
    private static final String JPA = "jpa-hibernate";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManager entityManager;

    // ---------------------------------------------------------------- helpers

    private ResultActions as(User user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        return mockMvc.perform(req.header("Authorization", bearer(user)));
    }

    private ResultActions startRaw(User user, String json) throws Exception {
        return as(user, post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode start(User user, String json) throws Exception {
        MvcResult result = startRaw(user, json).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions submitRaw(User user, long attemptId, String json) throws Exception {
        return as(user, post("/api/challenges/" + attemptId + "/submit")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Test questions are built with the correct option at index 1; this answers the first n correctly, the rest wrongly. */
    private String answers(JsonNode attemptView, int correctCount) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode list = body.putArray("answers");
        int i = 0;
        for (JsonNode question : attemptView.get("questions")) {
            ObjectNode answer = list.addObject();
            answer.put("questionId", question.get("id").asLong());
            answer.put("optionId", question.get("options").get(i < correctCount ? 1 : 0).get("id").asLong());
            i++;
        }
        return body.toString();
    }

    private JsonNode submit(User user, JsonNode attemptView, int correctCount) throws Exception {
        MvcResult result = submitRaw(user, attemptView.get("id").asLong(), answers(attemptView, correctCount))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void questions(String topic, Difficulty difficulty, int n, String prefix) {
        for (int i = 0; i < n; i++) {
            createQuestion(topic, difficulty, prefix + i);
        }
    }

    // ---------------------------------------------------------------- topics

    @Test
    void topicsListShowsOnlyActiveQuestionCounts() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        createQuestion(JWT, Difficulty.EASY, "active one");
        createQuestion(JWT, Difficulty.EASY, "active two");
        Question retired = createQuestion(JWT, Difficulty.EASY, "retired");
        retired.setActive(false);
        questionRepository.saveAndFlush(retired);

        as(sam, get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7)) // five earlier topics plus the JDBC topic (V7) and the Microservices topic (V10)
                .andExpect(jsonPath("$[?(@.slug=='jwt-security')].activeQuestionCount").value(2))
                .andExpect(jsonPath("$[?(@.slug=='jpa-hibernate')].activeQuestionCount").value(0));
    }

    @Test
    void everyArenaEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/topics")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/challenges/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/challenges/1/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/challenges/1/result")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/challenges/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/progress/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboard")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- starting an attempt

    @Test
    void startFiltersByTopicAndDifficultyAndSkipsInactiveQuestions() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        Question e1 = createQuestion(JWT, Difficulty.EASY, "e1");
        Question e2 = createQuestion(JWT, Difficulty.EASY, "e2");
        createQuestion(JWT, Difficulty.HARD, "h1");
        createQuestion(JPA, Difficulty.EASY, "other topic");
        Question retired = createQuestion(JWT, Difficulty.EASY, "retired");
        retired.setActive(false);
        questionRepository.saveAndFlush(retired);

        MvcResult result = startRaw(sam, "{\"topic\":\"jwt-security\",\"difficulty\":\"EASY\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/challenges/")))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.topicSlug").value(JWT))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andReturn();

        Set<Long> ids = new HashSet<>();
        objectMapper.readTree(result.getResponse().getContentAsString()).get("questions")
                .forEach(q -> ids.add(q.get("id").asLong()));
        assertThat(ids).containsExactlyInAnyOrder(e1.getId(), e2.getId());
    }

    @Test
    void startRespectsRequestedCountButNeverInventsQuestions() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 3, "q");

        assertThat(start(sam, "{\"topic\":\"jwt-security\",\"count\":2}").get("questions").size()).isEqualTo(2);
        assertThat(start(sam, "{\"topic\":\"jwt-security\",\"count\":20}").get("questions").size()).isEqualTo(3);
        // No body at all: every topic, any difficulty, default size.
        assertThat(start(sam, "{}").get("totalQuestions").asInt()).isEqualTo(3);
    }

    @Test
    void startRejectsInvalidRequests() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        createQuestion(JWT, Difficulty.EASY, "q");

        startRaw(sam, "{\"count\":0}").andExpect(status().isBadRequest());
        startRaw(sam, "{\"count\":21}").andExpect(status().isBadRequest());
        startRaw(sam, "{\"difficulty\":\"IMPOSSIBLE\"}").andExpect(status().isBadRequest());
        startRaw(sam, "{\"topic\":\"no-such-topic\"}").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        // Valid request, but nothing matches.
        startRaw(sam, "{\"topic\":\"jpa-hibernate\"}").andExpect(status().isConflict());
        assertThat(attemptRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- no answer leakage

    @Test
    void questionResponsesNeverContainCorrectAnswersOrExplanations() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 3, "q");

        MvcResult started = startRaw(sam, "{\"topic\":\"jwt-security\"}").andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("correct"))))
                .andExpect(content().string(not(containsString("explanation"))))
                .andExpect(content().string(not(containsString("Explanation for"))))
                .andReturn();
        long id = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();

        as(sam, get("/api/challenges/" + id)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("correct"))))
                .andExpect(content().string(not(containsString("xplanation"))));
        // Results (which reveal answers) are refused until the attempt has been submitted.
        as(sam, get("/api/challenges/" + id + "/result")).andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("xplanation"))))
                .andExpect(content().string(not(containsString("\"correct\""))));
    }

    // ---------------------------------------------------------------- scoring, persistence, explanations

    @Test
    void submissionIsScoredOnTheServerPersistedAndExplained() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 3, "question ");
        JsonNode attempt = start(sam, "{\"topic\":\"jwt-security\"}");

        // First answered correctly, second wrongly, third skipped.
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode list = body.putArray("answers");
        JsonNode q = attempt.get("questions");
        list.addObject().put("questionId", q.get(0).get("id").asLong()).put("optionId", q.get(0).get("options").get(1).get("id").asLong());
        list.addObject().put("questionId", q.get(1).get("id").asLong()).put("optionId", q.get(1).get("options").get(0).get("id").asLong());

        submitRaw(sam, attempt.get("id").asLong(), body.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.percentage").value(33.3))
                .andExpect(jsonPath("$.questions[0].correct").value(true))
                .andExpect(jsonPath("$.questions[0].answered").value(true))
                .andExpect(jsonPath("$.questions[1].correct").value(false))
                .andExpect(jsonPath("$.questions[2].answered").value(false))
                .andExpect(jsonPath("$.questions[2].correct").value(false))
                // Now (and only now) the correct option and the explanation are revealed.
                .andExpect(jsonPath("$.questions[0].options[1].correct").value(true))
                .andExpect(jsonPath("$.questions[0].options[0].correct").value(false))
                .andExpect(jsonPath("$.questions[0].explanation", containsString("Explanation for")));

        // Persisted: reload from the database, not from the persistence context.
        entityManager.flush();
        entityManager.clear();
        Attempt stored = attemptRepository.findById(attempt.get("id").asLong()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(stored.getScore()).isEqualTo(1);
        assertThat(stored.getSubmittedAt()).isNotNull();
        var answers = attemptAnswerRepository.findDetailedByAttemptId(stored.getId());
        assertThat(answers).extracting(a -> a.getCorrect()).containsExactly(true, false, false);
        assertThat(answers.get(2).getSelectedOption()).isNull();

        // The same figures are available later through the result endpoint.
        as(sam, get("/api/challenges/" + attempt.get("id").asLong() + "/result"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.questions[1].options[0].selected").value(true));
    }

    @Test
    void clientSuppliedScoreOrCorrectnessIsIgnored() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 2, "q");
        JsonNode attempt = start(sam, "{}");
        ObjectNode body = (ObjectNode) objectMapper.readTree(answers(attempt, 0));
        body.put("score", 99);
        body.withArray("answers").forEach(a -> ((ObjectNode) a).put("correct", true));

        submitRaw(sam, attempt.get("id").asLong(), body.toString())
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void anAttemptCanOnlyBeSubmittedOnce() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 2, "q");
        JsonNode attempt = start(sam, "{}");
        submit(sam, attempt, 0); // score 0

        // A second submission that would have scored 2 must be rejected and must not change the stored result.
        submitRaw(sam, attempt.get("id").asLong(), answers(attempt, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This challenge has already been submitted"));

        entityManager.flush();
        entityManager.clear();
        assertThat(attemptRepository.findById(attempt.get("id").asLong()).orElseThrow().getScore()).isZero();
    }

    @Test
    void invalidSubmissionsAreRejectedAndLeaveTheAttemptOpen() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 2, "mine ");
        Question foreign = createQuestion(JPA, Difficulty.EASY, "not in the attempt");
        JsonNode attempt = start(sam, "{\"topic\":\"jwt-security\"}");
        long id = attempt.get("id").asLong();
        long q0 = attempt.get("questions").get(0).get("id").asLong();
        long q1 = attempt.get("questions").get(1).get("id").asLong();
        long optionOfQ0 = attempt.get("questions").get(0).get("options").get(0).get("id").asLong();
        long optionOfQ1 = attempt.get("questions").get(1).get("options").get(0).get("id").asLong();

        // question that is not part of this attempt
        submitRaw(sam, id, "{\"answers\":[{\"questionId\":" + foreign.getId() + ",\"optionId\":"
                + foreign.getOptions().get(0).getId() + "}]}").andExpect(status().isBadRequest());
        // option that belongs to a different question
        submitRaw(sam, id, "{\"answers\":[{\"questionId\":" + q0 + ",\"optionId\":" + optionOfQ1 + "}]}")
                .andExpect(status().isBadRequest());
        // option id that does not exist
        submitRaw(sam, id, "{\"answers\":[{\"questionId\":" + q0 + ",\"optionId\":987654321}]}")
                .andExpect(status().isBadRequest());
        // the same question answered twice
        submitRaw(sam, id, "{\"answers\":[{\"questionId\":" + q0 + ",\"optionId\":" + optionOfQ0
                + "},{\"questionId\":" + q0 + ",\"optionId\":" + optionOfQ0 + "}]}").andExpect(status().isBadRequest());
        // malformed / missing fields
        submitRaw(sam, id, "{}").andExpect(status().isBadRequest());
        submitRaw(sam, id, "{\"answers\":[{\"questionId\":" + q0 + "}]}").andExpect(status().isBadRequest());
        submitRaw(sam, id, "{\"answers\":[null]}").andExpect(status().isBadRequest());
        submitRaw(sam, id, "not json").andExpect(status().isBadRequest());
        // a valid answer mixed with an invalid one must be rejected as a whole
        submitRaw(sam, id, "{\"answers\":[{\"questionId\":" + q0 + ",\"optionId\":" + optionOfQ0
                + "},{\"questionId\":" + q1 + ",\"optionId\":" + optionOfQ0 + "}]}").andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();
        Attempt stored = attemptRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(stored.getScore()).isNull();
        assertThat(attemptAnswerRepository.findDetailedByAttemptId(id)).allMatch(a -> a.getCorrect() == null);
        // and the attempt can still be submitted correctly afterwards
        submit(sam, attempt, 1);
    }

    @Test
    void anAttemptStillScoresAfterItsQuestionIsDeactivatedMidway() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 2, "q");
        JsonNode attempt = start(sam, "{}");
        for (Question q : questionRepository.findAll()) {
            q.setActive(false);
        }
        questionRepository.flush();

        submitRaw(sam, attempt.get("id").asLong(), answers(attempt, 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(2));
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void usersCannotSeeOrSubmitEachOthersAttempts() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        User eve = createUser("eve", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 2, "q");
        JsonNode attempt = start(sam, "{}");
        long id = attempt.get("id").asLong();

        as(eve, get("/api/challenges/" + id)).andExpect(status().isNotFound());
        as(eve, get("/api/challenges/" + id + "/result")).andExpect(status().isNotFound());
        submitRaw(eve, id, answers(attempt, 2)).andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        assertThat(attemptRepository.findById(id).orElseThrow().getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void historyContainsOnlyTheCallersAttemptsNewestFirst() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        User eve = createUser("eve", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 2, "q");
        JsonNode first = start(sam, "{}");
        submit(sam, first, 2);
        JsonNode second = start(sam, "{}");
        start(eve, "{}");

        as(sam, get("/api/challenges/me?size=1&page=0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second.get("id").asLong()))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));
        as(sam, get("/api/challenges/me?size=1&page=1"))
                .andExpect(jsonPath("$.content[0].id").value(first.get("id").asLong()))
                .andExpect(jsonPath("$.content[0].score").value(2));
    }

    // ---------------------------------------------------------------- progress and leaderboard

    @Test
    void progressIsAggregatedFromStoredAttempts() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        questions(JWT, Difficulty.EASY, 3, "j");
        createQuestion(JPA, Difficulty.HARD, "p");
        submit(sam, start(sam, "{\"topic\":\"jwt-security\"}"), 2); // 2 of 3
        submit(sam, start(sam, "{\"topic\":\"jpa-hibernate\"}"), 0); // 0 of 1
        start(sam, "{\"topic\":\"jwt-security\"}");                  // unsubmitted: must not count

        as(sam, get("/api/progress/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsCompleted").value(2))
                .andExpect(jsonPath("$.questionsAnswered").value(4))
                .andExpect(jsonPath("$.correctAnswers").value(2))
                .andExpect(jsonPath("$.accuracyPercent").value(50.0))
                .andExpect(jsonPath("$.byTopic.length()").value(2))
                .andExpect(jsonPath("$.byTopic[?(@.key=='jwt-security')].questions").value(3))
                .andExpect(jsonPath("$.byTopic[?(@.key=='jwt-security')].correct").value(2))
                .andExpect(jsonPath("$.byTopic[?(@.key=='jpa-hibernate')].correct").value(0))
                .andExpect(jsonPath("$.byDifficulty[?(@.key=='EASY')].questions").value(3))
                .andExpect(jsonPath("$.byDifficulty[?(@.key=='HARD')].correct").value(0))
                .andExpect(jsonPath("$.recentAttempts.length()").value(2));
    }

    @Test
    void progressForAUserWithNoAttemptsIsZero() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        as(sam, get("/api/progress/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsCompleted").value(0))
                .andExpect(jsonPath("$.accuracyPercent").value(0.0))
                .andExpect(jsonPath("$.byTopic.length()").value(0));
    }

    @Test
    void leaderboardRanksStudentsByPointsFromSubmittedAttemptsOnly() throws Exception {
        questions(JWT, Difficulty.EASY, 3, "j");
        createQuestion(JPA, Difficulty.EASY, "p");
        User ann = createUser("ann", Role.STUDENT);
        User bob = createUser("bob", Role.STUDENT);
        User cat = createUser("cat", Role.STUDENT);
        User dan = createUser("dan", Role.STUDENT);
        User tina = createUser("tina", Role.TEACHER);
        User gone = createUser("gone", Role.STUDENT);

        submit(ann, start(ann, "{\"topic\":\"jwt-security\"}"), 3);   // 3 points / 3 questions
        submit(bob, start(bob, "{\"topic\":\"jwt-security\"}"), 2);   // 2 points / 3 questions
        submit(cat, start(cat, "{\"topic\":\"jwt-security\"}"), 2);   // 2 points / 3 questions -> ties with bob
        submit(cat, start(cat, "{\"topic\":\"jpa-hibernate\"}"), 0);  // +0 points, +1 question -> below bob
        start(dan, "{\"topic\":\"jwt-security\"}");                    // never submitted -> not ranked
        submit(tina, start(tina, "{\"topic\":\"jwt-security\"}"), 3); // teacher -> not ranked
        submit(gone, start(gone, "{\"topic\":\"jwt-security\"}"), 3); // becomes disabled -> not ranked
        gone.setEnabled(false);
        userRepository.saveAndFlush(gone);

        MvcResult result = as(ann, get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").value("ann"))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].points").value(3))
                .andExpect(jsonPath("$[1].username").value("bob"))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[2].username").value("cat"))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[2].points").value(2))
                .andExpect(jsonPath("$[2].attempts").value(2))
                .andExpect(content().string(not(containsString("example.com"))))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("email").doesNotContain("passwordHash");

        // Topic filter: only attempts of that topic count, so cat's JPA attempt disappears and she ties with bob.
        as(ann, get("/api/leaderboard?topic=jwt-security"))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[2].rank").value(2));
        as(ann, get("/api/leaderboard?topic=jpa-hibernate"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].username").value("cat"));
        as(ann, get("/api/leaderboard?limit=2")).andExpect(jsonPath("$.length()").value(2));
        as(ann, get("/api/leaderboard?topic=nope")).andExpect(status().isBadRequest());
    }
}
