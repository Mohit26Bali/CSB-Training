package com.csb.cinescope.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.entity.QuoteCategory;
import com.csb.cinescope.repository.QuoteRepository;

/** The quote list: the seed file is well formed, fills the table on start-up, and has the variety the quiz needs. */
class QuoteSeederTest extends AbstractApiTest {

    @Autowired
    QuoteRepository repository;

    @Test
    void the_table_is_filled_from_the_seed_file_at_start_up() {
        assertThat(repository.count()).isEqualTo(QuoteSeeder.read().size()).isGreaterThan(200);
    }

    @Test
    void every_category_has_enough_quotes_for_a_quiz() {
        Map<QuoteCategory, Long> perCategory = QuoteSeeder.read().stream().collect(Collectors.groupingBy(Quote::getCategory, Collectors.counting()));
        for (QuoteCategory category : QuoteCategory.values()) {
            assertThat(perCategory.get(category)).as(category.name()).isGreaterThanOrEqualTo(30);
        }
    }

    @Test
    void no_line_is_repeated_and_every_row_is_complete() {
        List<Quote> rows = QuoteSeeder.read();
        Set<String> seen = new HashSet<>();
        for (Quote q : rows) {
            assertThat(seen.add(q.getTitle() + "|" + q.getLine())).as("duplicate: " + q.getLine()).isTrue();
            assertThat(q.getLine()).isNotBlank().doesNotContain("|").hasSizeLessThanOrEqualTo(500);
            assertThat(q.getSpeaker()).isNotBlank();
            assertThat(q.getMediaType()).isIn("movie", "tv");
            assertThat(q.getTmdbId()).isPositive();
            assertThat(q.getReleaseYear()).isBetween(1930, 2026);
        }
    }

    @Test
    void no_title_has_more_than_four_quotes_so_the_quiz_is_not_repetitive() {
        Map<String, Long> perTitle = QuoteSeeder.read().stream().collect(Collectors.groupingBy(q -> q.getTmdbId() + q.getMediaType(), Collectors.counting()));
        assertThat(perTitle.values()).allMatch(count -> count <= 4);
    }
}
