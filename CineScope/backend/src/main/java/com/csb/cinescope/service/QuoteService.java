package com.csb.cinescope.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.entity.QuoteCategory;
import com.csb.cinescope.entity.QuoteSeen;
import com.csb.cinescope.repository.QuoteRepository;
import com.csb.cinescope.repository.QuoteSeenRepository;

/**
 * Chooses quotes so that a person does not see the same ones again and again.
 *
 * <p>For a signed-in person ({@link #next}): quotes they have not been shown yet are picked at random, recorded in
 * {@code quote_seen} (migration V5), and only when every quote of the category has been seen does a new round start, which
 * also avoids the last few quotes of the previous round so the join is not a repeat. Within one pick the same title is not
 * used twice in a row. History is kept per category, so finishing the anime does not use up the films.
 *
 * <p>For a visitor who is not signed in ({@link #sample}): nothing can be stored on the server, so the browser sends the ids it
 * has already shown and the server avoids those. Used by {@code service/ShowcaseService}; the quiz endpoint will use {@link #next}.
 * Tested by {@code QuoteServiceTest}.
 */
@Service
public class QuoteService {

    /** How many quotes at the end of a finished round are kept out of the start of the next one. */
    static final int RECENT_GUARD = 5;

    private final QuoteRepository quotes;
    private final QuoteSeenRepository seen;
    private final Random random;

    @Autowired
    public QuoteService(QuoteRepository quotes, QuoteSeenRepository seen) {
        this(quotes, seen, new Random());
    }

    QuoteService(QuoteRepository quotes, QuoteSeenRepository seen, Random random) {
        this.quotes = quotes;
        this.seen = seen;
        this.random = random;
    }

    /** {@code count} quotes this person has not seen in the category (all categories when null); they are recorded as seen. */
    @Transactional
    public List<Quote> next(long userId, QuoteCategory category, int count) {
        if (category == null) {
            return mixed(userId, count);
        }
        List<Quote> pool = pool(category);
        List<Quote> picked = new ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            Set<Long> already = new HashSet<>(seen.seenAmong(userId, ids(pool)));
            List<Quote> fresh = pool.stream().filter(q -> !already.contains(q.getId()) && picked.stream().noneMatch(p -> p.getId().equals(q.getId()))).collect(Collectors.toCollection(ArrayList::new));
            if (fresh.isEmpty()) {
                // The round is finished: start again. The quotes that ended it stay marked as seen, so the new round cannot
                // open with them (even when the quiz asks for one quote at a time); they come back in the round after.
                Set<Long> keep = new HashSet<>(lastOf(picked));
                seen.findTop5ByUserIdAndQuoteIdInOrderBySeenAtDescIdDesc(userId, ids(pool)).forEach(s -> keep.add(s.getQuoteId()));
                if (pool.size() <= 2 * RECENT_GUARD) {
                    keep.clear(); // a tiny category: guarding would leave almost nothing to show
                }
                List<Long> forgotten = pool.stream().map(Quote::getId).filter(id -> !keep.contains(id)).toList();
                if (!forgotten.isEmpty()) {
                    seen.forget(userId, forgotten);
                }
                fresh = pool.stream().filter(q -> !keep.contains(q.getId()) && picked.stream().noneMatch(p -> p.getId().equals(q.getId()))).collect(Collectors.toCollection(ArrayList::new));
                if (fresh.isEmpty()) {
                    break; // fewer quotes in the pool than were asked for
                }
            }
            picked.addAll(spread(fresh, count - picked.size(), picked.isEmpty() ? null : picked.get(picked.size() - 1)));
        }
        List<QuoteSeen> marks = new ArrayList<>();
        Set<Long> marked = new HashSet<>(seen.seenAmong(userId, ids(picked)));
        for (Quote q : picked) {
            if (!marked.contains(q.getId())) {
                marks.add(new QuoteSeen(userId, q.getId()));
            }
        }
        seen.saveAll(marks);
        return picked;
    }

    /** {@code count} random quotes for a visitor, avoiding the ids the browser says it has shown; tops up from the rest if needed. */
    @Transactional(readOnly = true)
    public List<Quote> sample(Collection<Long> exclude, int count) {
        List<Quote> all = quotes.findAll();
        Set<Long> skip = new HashSet<>(exclude);
        List<Quote> fresh = all.stream().filter(q -> !skip.contains(q.getId())).collect(Collectors.toCollection(ArrayList::new));
        List<Quote> picked = new ArrayList<>(rotate(fresh, count, null));
        if (picked.size() < count) {
            Set<Long> taken = picked.stream().map(Quote::getId).collect(Collectors.toSet());
            List<Quote> rest = all.stream().filter(q -> !taken.contains(q.getId())).collect(Collectors.toCollection(ArrayList::new));
            picked.addAll(rotate(rest, count - picked.size(), picked.isEmpty() ? null : picked.get(picked.size() - 1)));
        }
        return picked;
    }

    /**
     * Up to {@code count} of these, taking turns between the categories (in a random order) so that the pick is a mix rather than
     * mostly the biggest category, and never the same title twice in a row.
     */
    private List<Quote> rotate(List<Quote> candidates, int count, Quote previous) {
        List<Quote> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        java.util.Map<QuoteCategory, java.util.Deque<Quote>> byCategory = new java.util.EnumMap<>(QuoteCategory.class);
        for (Quote q : shuffled) {
            byCategory.computeIfAbsent(q.getCategory(), c -> new java.util.ArrayDeque<>()).add(q);
        }
        List<QuoteCategory> order = new ArrayList<>(byCategory.keySet());
        Collections.shuffle(order, random);
        List<Quote> out = new ArrayList<>();
        Quote last = previous;
        int turn = 0;
        while (out.size() < count && byCategory.values().stream().anyMatch(d -> !d.isEmpty())) {
            java.util.Deque<Quote> queue = byCategory.get(order.get(turn++ % order.size()));
            if (queue.isEmpty()) {
                continue;
            }
            Quote chosen = null;
            for (Quote q : queue) {
                if (last == null || !sameTitle(q, last)) {
                    chosen = q;
                    break;
                }
            }
            if (chosen == null) {
                chosen = queue.peekFirst();
            }
            queue.remove(chosen);
            out.add(chosen);
            last = chosen;
        }
        return out;
    }

    /**
     * A mix of all four categories: each contributes an equal share (the remainder going to categories chosen at random), taken from
     * that category's own unseen quotes, so a mixed pick is not 60% Hollywood just because Hollywood has the most quotes. A category
     * that cannot supply its share (it has fewer quotes than asked for) supplies what it has; the mix is not topped up from others.
     */
    private List<Quote> mixed(long userId, int count) {
        List<QuoteCategory> order = new ArrayList<>(List.of(QuoteCategory.values()));
        Collections.shuffle(order, random);
        List<Quote> out = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            int share = count / order.size() + (i < count % order.size() ? 1 : 0);
            if (share > 0) {
                out.addAll(next(userId, order.get(i), share));
            }
        }
        Collections.shuffle(out, random);
        return out;
    }

    private List<Quote> pool(QuoteCategory category) {
        return quotes.findByCategory(category.name());
    }

    private static List<Long> ids(List<Quote> list) {
        return list.stream().map(Quote::getId).toList();
    }

    private static List<Long> lastOf(List<Quote> picked) {
        return picked.subList(Math.max(0, picked.size() - RECENT_GUARD), picked.size()).stream().map(Quote::getId).toList();
    }

    /** Up to {@code count} of these in random order, never the same title twice in a row (including right after {@code previous}). */
    private List<Quote> spread(List<Quote> candidates, int count, Quote previous) {
        List<Quote> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        List<Quote> out = new ArrayList<>();
        Quote last = previous;
        while (out.size() < count && !shuffled.isEmpty()) {
            Quote chosen = null;
            for (Quote q : shuffled) {
                if (last == null || !sameTitle(q, last)) {
                    chosen = q;
                    break;
                }
            }
            if (chosen == null) {
                chosen = shuffled.get(0); // only one title left; a repeat of the title is unavoidable
            }
            shuffled.remove(chosen);
            out.add(chosen);
            last = chosen;
        }
        return out;
    }

    private static boolean sameTitle(Quote a, Quote b) {
        return a.getTmdbId() == b.getTmdbId() && a.getMediaType().equals(b.getMediaType());
    }
}
