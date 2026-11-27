package com.springlab.repository;

import com.springlab.support.AbstractSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the Flyway seed (V3): every seeded question must be well-formed so students are never shown a broken one. */
class SeedDataIntegrationTest extends AbstractSpringBootTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyTopicHasSeedQuestions() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select t.slug, count(q.id) as n from topics t left join questions q "
                        + "on q.topic_id = t.id and q.created_by is null group by t.slug");
        assertThat(rows).hasSize(7);
        assertThat(rows).allSatisfy(row -> assertThat(((Number) row.get("n")).intValue()).isGreaterThanOrEqualTo(4));
        assertThat(rows).extracting(r -> r.get("slug")).contains("rest-lifecycle", "jwt-security", "jpa-hibernate", "jdbc", "microservices");
    }

    @Test
    void everySeedQuestionHasExactlyOneCorrectOptionAndAnExplanation() {
        List<Map<String, Object>> broken = jdbc.queryForList("""
                select q.id from questions q
                left join question_options o on o.question_id = q.id
                where q.created_by is null
                group by q.id, q.explanation
                having sum(o.correct) <> 1 or count(o.id) < 2 or count(o.id) > 6 or length(trim(q.explanation)) = 0
                """);
        assertThat(broken).as("malformed seed questions").isEmpty();
    }

    @Test
    void seedOptionTextsAreUniquePerQuestion() {
        List<Map<String, Object>> duplicates = jdbc.queryForList("""
                select question_id, option_text from question_options
                group by question_id, option_text having count(*) > 1
                """);
        assertThat(duplicates).isEmpty();
    }
}
