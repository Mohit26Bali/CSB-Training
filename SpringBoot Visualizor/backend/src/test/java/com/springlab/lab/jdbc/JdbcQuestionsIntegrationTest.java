package com.springlab.lab.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The JDBC topic of the Challenge Arena: content quality and the existing no-leak rule, against the real seeded questions. */
class JdbcQuestionsIntegrationTest extends AbstractJdbcLabTest {

    @AfterEach
    void removeAttempts() {
        // Runs before the user is deleted by the base class.
        jdbc.update("delete aa from attempt_answers aa join attempts a on a.id = aa.attempt_id join users u on u.id = a.user_id where u.username like 'jdbc\\_lab\\_%'");
        jdbc.update("delete a from attempts a join users u on u.id = a.user_id where u.username like 'jdbc\\_lab\\_%'");
    }

    @Test
    void theJdbcTopicHasAtLeastTwentyFiveWellFormedQuestions() {
        Long topic = jdbc.queryForObject("select id from topics where slug = 'jdbc'", Long.class);
        List<Map<String, Object>> questions = jdbc.queryForList("select id, explanation, active from questions where topic_id = ?", topic);
        assertThat(questions.size()).isGreaterThanOrEqualTo(25);
        for (Map<String, Object> q : questions) {
            List<Map<String, Object>> options = jdbc.queryForList("select option_text, correct, sort_order from question_options where question_id = ? order by sort_order", q.get("id"));
            assertThat(options).hasSize(4);
            assertThat(options.stream().filter(o -> Boolean.TRUE.equals(o.get("correct")) || Integer.valueOf(1).equals(o.get("correct"))).count()).as("exactly one correct option").isEqualTo(1);
            assertThat(options.stream().map(o -> o.get("option_text")).distinct().count()).isEqualTo(4);
            assertThat(String.valueOf(q.get("explanation")).length()).isGreaterThan(80);
            assertThat(Boolean.TRUE.equals(q.get("active")) || Integer.valueOf(1).equals(q.get("active"))).isTrue();
        }
    }

    @Test
    void theCorrectAnswerIsSpreadOverAllPositionsWithNoLongRuns() {
        Long topic = jdbc.queryForObject("select id from topics where slug = 'jdbc'", Long.class);
        List<Integer> positions = new ArrayList<>();
        for (Map<String, Object> q : jdbc.queryForList("select id from questions where topic_id = ? order by id", topic)) {
            positions.add(jdbc.queryForObject("select sort_order from question_options where question_id = ? and correct = 1", Integer.class, q.get("id")));
        }
        for (int position = 0; position < 4; position++) {
            final int p = position;
            long count = positions.stream().filter(x -> x == p).count();
            assertThat(count).as("position " + p).isBetween(positions.size() / 4L - 2, positions.size() / 4L + 2);
        }
        int run = 1;
        for (int i = 1; i < positions.size(); i++) {
            run = positions.get(i).equals(positions.get(i - 1)) ? run + 1 : 1;
            assertThat(run).as("same position repeated").isLessThanOrEqualTo(2);
        }
    }

    @Test
    void startingAJdbcChallengeRevealsNeitherAnswersNorExplanations() throws Exception {
        String body = mockMvc.perform(post("/api/challenges").header("Authorization", bearer(student)).contentType("application/json")
                        .content("{\"topic\":\"jdbc\",\"count\":20}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        JsonNode attempt = objectMapper.readTree(body);
        assertThat(attempt.get("totalQuestions").asInt()).isEqualTo(20);
        assertThat(body).doesNotContain("\"correct\"").doesNotContain("explanation");
        assertThat(attempt.get("questions").get(0).get("options")).hasSize(4);
    }

    @Test
    void theTopicIsListedWithItsActiveQuestionCount() throws Exception {
        JsonNode topics = getJson("/api/topics");
        JsonNode jdbcTopic = null;
        for (JsonNode t : topics) {
            if (t.get("slug").asText().equals("jdbc")) {
                jdbcTopic = t;
            }
        }
        assertThat(jdbcTopic).isNotNull();
        assertThat(jdbcTopic.get("activeQuestionCount").asInt()).isGreaterThanOrEqualTo(25);
    }
}
