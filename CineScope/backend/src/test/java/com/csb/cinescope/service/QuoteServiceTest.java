package com.csb.cinescope.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.entity.QuoteCategory;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.repository.QuoteRepository;
import com.csb.cinescope.repository.QuoteSeenRepository;

/**
 * The no-repeat rules of {@link QuoteService}, against the real seeded quotes and an in-memory database: nothing repeats until a
 * category is used up, categories keep separate histories, the same title never comes twice in a row, a new round starts cleanly,
 * people do not affect each other, and deleting an account removes its history.
 */
class QuoteServiceTest extends AbstractApiTest {

    @Autowired QuoteService service;
    @Autowired QuoteRepository quoteRepository;
    @Autowired QuoteSeenRepository seenRepository;

    private User person(String name) {
        return userRepository.save(new User(name, name + "@example.com", "x", name, "IN"));
    }

    private long size(QuoteCategory category) {
        return quoteRepository.findByCategory(category.name()).size();
    }

    @Test
    void a_whole_category_is_served_without_a_single_repeat() {
        User user = person("quizzer1");
        long total = size(QuoteCategory.ANIME);
        Set<Long> seen = new HashSet<>();
        for (long served = 0; served < total; served += 5) {
            for (Quote q : service.next(user.getId(), QuoteCategory.ANIME, (int) Math.min(5, total - served))) {
                assertThat(seen.add(q.getId())).as("repeated: " + q.getLine()).isTrue();
                assertThat(q.getCategory()).isEqualTo(QuoteCategory.ANIME);
            }
        }
        assertThat(seen).hasSize((int) total);
    }

    @Test
    void after_the_last_quote_a_new_round_starts_and_does_not_open_with_the_ones_that_just_ended_the_old_one() {
        User user = person("quizzer2");
        int total = (int) size(QuoteCategory.TV);
        List<Quote> firstRound = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            firstRound.addAll(service.next(user.getId(), QuoteCategory.TV, 1));
        }
        assertThat(firstRound).hasSize(total).extracting(Quote::getId).doesNotHaveDuplicates();

        List<Quote> secondRoundStart = service.next(user.getId(), QuoteCategory.TV, 5);
        assertThat(secondRoundStart).hasSize(5).extracting(Quote::getId).doesNotHaveDuplicates();
        List<Long> lastFive = firstRound.subList(total - QuoteService.RECENT_GUARD, total).stream().map(Quote::getId).toList();
        assertThat(secondRoundStart).extracting(Quote::getId).doesNotContainAnyElementsOf(lastFive);
    }

    @Test
    void one_pick_spanning_the_end_of_a_round_still_has_no_duplicates() {
        User user = person("quizzer3");
        int total = (int) size(QuoteCategory.BOLLYWOOD);
        service.next(user.getId(), QuoteCategory.BOLLYWOOD, total - 3);
        List<Quote> across = service.next(user.getId(), QuoteCategory.BOLLYWOOD, 8);
        assertThat(across).hasSize(8).extracting(Quote::getId).doesNotHaveDuplicates();
    }

    @Test
    void categories_keep_separate_histories() {
        User user = person("quizzer4");
        int anime = (int) size(QuoteCategory.ANIME);
        service.next(user.getId(), QuoteCategory.ANIME, anime); // all of anime used up
        List<Quote> films = service.next(user.getId(), QuoteCategory.HOLLYWOOD, 10);
        assertThat(films).hasSize(10).allMatch(q -> q.getCategory() == QuoteCategory.HOLLYWOOD);
        // and the film quotes just served are not repeated either
        List<Quote> more = service.next(user.getId(), QuoteCategory.HOLLYWOOD, 10);
        assertThat(more).extracting(Quote::getId).doesNotContainAnyElementsOf(films.stream().map(Quote::getId).toList());
    }

    @Test
    void the_same_title_never_comes_twice_in_a_row() {
        User user = person("quizzer5");
        List<Quote> served = service.next(user.getId(), QuoteCategory.HOLLYWOOD, 60);
        for (int i = 1; i < served.size(); i++) {
            Quote a = served.get(i - 1);
            Quote b = served.get(i);
            assertThat(a.getTmdbId() == b.getTmdbId() && a.getMediaType().equals(b.getMediaType())).as(a.getTitle() + " twice in a row").isFalse();
        }
    }

    @Test
    void people_do_not_affect_each_others_history() {
        User first = person("quizzer6");
        User second = person("quizzer7");
        List<Long> firstSaw = service.next(first.getId(), QuoteCategory.TV, 20).stream().map(Quote::getId).toList();
        // the second person starts from nothing: over many picks they will reach quotes the first person already saw
        Set<Long> secondSaw = new HashSet<>();
        for (Quote q : service.next(second.getId(), QuoteCategory.TV, (int) size(QuoteCategory.TV))) {
            secondSaw.add(q.getId());
        }
        assertThat(secondSaw).containsAll(firstSaw);
    }

    @Test
    void asking_for_more_than_a_category_holds_returns_every_quote_once_and_does_not_loop() {
        User user = person("quizzer8");
        int total = (int) size(QuoteCategory.ANIME);
        List<Quote> all = service.next(user.getId(), QuoteCategory.ANIME, total + 20);
        assertThat(all).hasSize(total).extracting(Quote::getId).doesNotHaveDuplicates();
    }

    @Test
    void a_mixed_pick_takes_a_fair_share_from_every_category_not_a_share_by_size() {
        User user = person("quizzer11");
        for (int round = 0; round < 5; round++) {
            List<Quote> mixed = service.next(user.getId(), null, 10);
            assertThat(mixed).hasSize(10).extracting(Quote::getId).doesNotHaveDuplicates();
            // ten over four categories: two each and two categories with a third; no category is missing or dominant
            for (QuoteCategory category : QuoteCategory.values()) {
                assertThat(mixed.stream().filter(q -> q.getCategory() == category).count()).as(category.name()).isBetween(2L, 3L);
            }
        }
    }

    @Test
    void with_no_category_all_four_are_used() {
        User user = person("quizzer9");
        List<Quote> mixed = service.next(user.getId(), null, 200);
        assertThat(mixed).extracting(Quote::getCategory).containsAll(List.of(QuoteCategory.values()));
        assertThat(mixed).extracting(Quote::getId).doesNotHaveDuplicates();
    }

    @Test
    void a_visitor_sample_avoids_what_the_browser_already_showed_and_tops_up_when_nothing_is_left() {
        List<Quote> first = service.sample(List.of(), 12);
        assertThat(first).hasSize(12).extracting(Quote::getId).doesNotHaveDuplicates();

        List<Long> shown = first.stream().map(Quote::getId).toList();
        assertThat(service.sample(shown, 12)).extracting(Quote::getId).doesNotContainAnyElementsOf(shown);

        List<Long> everything = quoteRepository.findAll().stream().map(Quote::getId).toList();
        assertThat(service.sample(everything, 12)).hasSize(12); // the browser has seen the lot: start over instead of showing nothing
    }

    @Test
    void deleting_an_account_removes_its_history() {
        User user = person("quizzer10");
        service.next(user.getId(), QuoteCategory.TV, 10);
        assertThat(seenRepository.findAll()).anyMatch(s -> s.getUserId() == user.getId());

        userRepository.delete(user);
        userRepository.flush();

        assertThat(seenRepository.findAll()).noneMatch(s -> s.getUserId() == user.getId());
    }
}
