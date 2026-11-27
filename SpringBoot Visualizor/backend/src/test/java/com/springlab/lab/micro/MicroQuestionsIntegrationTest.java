package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Microservices topic of the Challenge Arena, against the questions Flyway seeded (V10): their quality, and the existing Arena rules
 * (no answers before submission, server-side scoring, one submission, attempt ownership) applied to this topic. Nothing about the Arena
 * itself is special-cased for this topic.
 */
class MicroQuestionsIntegrationTest extends AbstractMicroLabTest {

    private static final String TOPIC = "microservices";

    @AfterEach
    void removeAttempts() {
        // Runs before the users are deleted by the base class.
        jdbc.update("delete aa from attempt_answers aa join attempts a on a.id = aa.attempt_id join users u on u.id = a.user_id where u.username like 'micro\\_lab\\_%'");
        jdbc.update("delete a from attempts a join users u on u.id = a.user_id where u.username like 'micro\\_lab\\_%'");
    }

    private Long topicId() {
        return jdbc.queryForObject("select id from topics where slug = ?", Long.class, TOPIC);
    }

    private static boolean yes(Object flag) {
        return Boolean.TRUE.equals(flag) || Integer.valueOf(1).equals(flag);
    }

    private JsonNode start(User user, String json) throws Exception {
        return json(mockMvc.perform(post("/api/challenges").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()));
    }

    private ResultActions submit(User user, long attemptId, String body) throws Exception {
        return mockMvc.perform(post("/api/challenges/" + attemptId + "/submit").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Answers every question of the attempt: the first {@code correctCount} with the stored correct option, the rest with a wrong one. */
    private String answers(JsonNode attempt, int correctCount) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode list = body.putArray("answers");
        int i = 0;
        for (JsonNode question : attempt.get("questions")) {
            long questionId = question.get("id").asLong();
            boolean wantCorrect = i++ < correctCount;
            Long option = jdbc.queryForObject("select id from question_options where question_id = ? and correct = ? order by sort_order limit 1",
                    Long.class, questionId, wantCorrect);
            list.addObject().put("questionId", questionId).put("optionId", option);
        }
        return body.toString();
    }

    @Test
    void theTopicHasWellFormedQuestionsWithExactlyOneCorrectOption() {
        List<Map<String, Object>> questions = jdbc.queryForList("select id, question_text, explanation, active, created_by from questions where topic_id = ?", topicId());
        assertThat(questions.size()).isBetween(30, 40);
        for (Map<String, Object> q : questions) {
            List<Map<String, Object>> options = jdbc.queryForList("select option_text, correct from question_options where question_id = ? order by sort_order", q.get("id"));
            assertThat(options).hasSize(4);
            assertThat(options.stream().filter(o -> yes(o.get("correct"))).count()).as("exactly one correct option: " + q.get("question_text")).isEqualTo(1);
            assertThat(options.stream().map(o -> o.get("option_text")).distinct().count()).isEqualTo(4);
            assertThat(String.valueOf(q.get("explanation")).length()).isGreaterThan(80);
            assertThat(yes(q.get("active"))).isTrue();
            assertThat(q.get("created_by")).as("seed content is authored by SpringLab").isNull();
            assertThat(options.stream().map(o -> String.valueOf(o.get("option_text")).toLowerCase()))
                    .as("no catch-all options").noneMatch(t -> t.contains("all of the above") || t.contains("none of the above"));
        }
    }

    @Test
    void theQuestionsSpreadOverAllDifficultiesAndTheCorrectAnswerOverAllPositions() {
        Map<String, Long> byDifficulty = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select difficulty, count(*) as n from questions where topic_id = ? group by difficulty", topicId())) {
            byDifficulty.put((String) row.get("difficulty"), ((Number) row.get("n")).longValue());
        }
        assertThat(byDifficulty.keySet()).containsExactlyInAnyOrder("EASY", "MEDIUM", "HARD");
        assertThat(byDifficulty.values()).allSatisfy(n -> assertThat(n).isGreaterThanOrEqualTo(5));

        List<Integer> positions = new ArrayList<>();
        for (Map<String, Object> q : jdbc.queryForList("select id from questions where topic_id = ? order by id", topicId())) {
            positions.add(jdbc.queryForObject("select sort_order from question_options where question_id = ? and correct = 1", Integer.class, q.get("id")));
        }
        for (int p = 0; p < 4; p++) {
            final int position = p;
            assertThat(positions.stream().filter(x -> x == position).count()).as("position " + p)
                    .isBetween(positions.size() / 4L - 2, positions.size() / 4L + 2);
        }
        int run = 1;
        for (int i = 1; i < positions.size(); i++) {
            run = positions.get(i).equals(positions.get(i - 1)) ? run + 1 : 1;
            assertThat(run).as("same position repeated").isLessThanOrEqualTo(2);
        }
    }

    @Test
    void questionsDescribeTheSimulatorHonestlyAndHoldNoSecrets() {
        String all = jdbc.queryForList("""
                select concat(q.question_text, ' ', q.explanation, ' ', group_concat(o.option_text separator ' ')) as t
                from questions q join question_options o on o.question_id = q.id where q.topic_id = ? group by q.id, q.question_text, q.explanation
                """, String.class, topicId()).toString();
        // the simulator is described as simulated: the named products only ever appear to say the lab is not them
        assertThat(all).doesNotContainIgnoringCase("exactly-once delivery is guaranteed").doesNotContainIgnoringCase("automatically recovers");
        assertThat(all).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("jdbc:mysql").doesNotContainIgnoringCase("localhost");
        for (String product : List.of("Kafka", "RabbitMQ", "Eureka", "Resilience4j")) {
            assertThat(all).as(product + " is not described as something the lab uses").doesNotContainPattern("(?i)(uses|built on|implemented with|integrates with) " + product);
        }
    }

    @Test
    void theTopicIsListedAndStartingAChallengeRevealsNeitherAnswersNorExplanations() throws Exception {
        mockMvc.perform(get("/api/topics").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='microservices')].activeQuestionCount").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(30))));

        String body = mockMvc.perform(post("/api/challenges").header("Authorization", bearer(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"microservices\",\"count\":20}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode attempt = objectMapper.readTree(body);
        assertThat(attempt.get("totalQuestions").asInt()).isEqualTo(20);
        assertThat(attempt.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(body).doesNotContain("\"correct\"").doesNotContain("explanation");
        assertThat(attempt.get("questions").get(0).get("options")).hasSize(4);

        // results (which reveal answers) are refused until submission
        mockMvc.perform(get("/api/challenges/" + attempt.get("id").asLong() + "/result").header("Authorization", bearer(student)))
                .andExpect(status().isConflict());
    }

    @Test
    void scoringHappensOnTheServerAndASecondSubmissionIsRefused() throws Exception {
        JsonNode attempt = start(student, "{\"topic\":\"microservices\",\"count\":10}");
        long id = attempt.get("id").asLong();

        // a client-supplied "correct" flag is ignored: 6 of 10 answered correctly means a score of 6
        ObjectNode body = (ObjectNode) objectMapper.readTree(answers(attempt, 6));
        body.withArray("answers").forEach(a -> ((ObjectNode) a).put("correct", true));
        submit(student, id, body.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(6))
                .andExpect(jsonPath("$.totalQuestions").value(10))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        // a second submission is refused and does not change the score
        submit(student, id, answers(attempt, 10)).andExpect(status().isConflict());
        mockMvc.perform(get("/api/challenges/" + id + "/result").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(6));
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Integer.class, id)).isEqualTo(6);
    }

    @Test
    void anAttemptBelongsToItsOwnerAndOthersGetNotFound() throws Exception {
        User other = newUser("micro_lab_other_" + System.nanoTime(), Role.STUDENT);
        JsonNode attempt = start(student, "{\"topic\":\"microservices\",\"count\":5}");
        long id = attempt.get("id").asLong();

        mockMvc.perform(get("/api/challenges/" + id).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/challenges/" + id + "/result").header("Authorization", bearer(other))).andExpect(status().isNotFound());
        submit(other, id, answers(attempt, 5)).andExpect(status().isNotFound());
        // the owner's attempt is untouched by the foreign attempts
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, id)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void everyRoleCanTakeTheChallengeAndTheExistingTopicsAreStillListed() throws Exception {
        Set<String> expected = Set.of("java-core", "spring-boot-basics", "rest-lifecycle", "jwt-security", "jpa-hibernate", "jdbc", "microservices");
        for (Role role : List.of(Role.STUDENT, Role.TEACHER, Role.ADMIN)) {
            User user = role == Role.STUDENT ? student : newUser("micro_lab_" + role.name().toLowerCase() + "_" + System.nanoTime(), role);
            JsonNode topics = json(mockMvc.perform(get("/api/topics").header("Authorization", bearer(user))).andExpect(status().isOk()));
            List<String> slugs = new ArrayList<>();
            topics.forEach(t -> slugs.add(t.get("slug").asText()));
            assertThat(slugs).containsAll(expected);
            start(user, "{\"topic\":\"microservices\",\"count\":3}");
        }
    }
}
