package com.csb.cinescope.service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.ShowcaseSlide;
import com.csb.cinescope.dto.TitleSummary;
import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.mapper.TitleMapper;
import com.csb.cinescope.tmdb.TmdbGateway;
import com.csb.cinescope.tmdb.TmdbQuery;

/**
 * The picture carousel of the sign-in and sign-up screens: films and shows, each with a famous line. TMDB has no dialogue
 * data, so the lines live in the {@code quotes} table (seeded by {@code config/QuoteSeeder}); {@code service/QuoteService} picks a
 * varied handful, avoiding the quotes the visitor's browser says it has already shown, and the pictures, title and year come live
 * from TMDB through {@code tmdb/TmdbGateway} (each title cached for an hour, so the screens stay fast), mapped by
 * {@code mapper/TitleMapper}.
 *
 * <p>A title that cannot be loaded, or has no backdrop, is simply left out; if TMDB is down altogether the list is empty and the
 * frontend shows a plain background instead. Called by {@code controller/ShowcaseController}. The titles are fetched in parallel
 * on the shared catalogue thread pool.
 */
@Service
public class ShowcaseService {

    /** How many slides a visit asks for; a few more quotes are picked than shown, because some titles have no backdrop. */
    public static final int SLIDES = 12;
    private static final int PICKED = SLIDES + 4;

    private final QuoteService quoteService;
    private final TmdbGateway tmdb;
    private final TitleMapper mapper;
    private final CineScopeProperties properties;
    private final ExecutorService executor;

    public ShowcaseService(QuoteService quoteService, TmdbGateway tmdb, TitleMapper mapper, CineScopeProperties properties, ExecutorService catalogExecutor) {
        this.quoteService = quoteService;
        this.tmdb = tmdb;
        this.mapper = mapper;
        this.properties = properties;
        this.executor = catalogExecutor;
    }

    /** A varied set of slides, none of the quotes in {@code alreadyShown}; titles that could not be loaded are missing. */
    public List<ShowcaseSlide> slides(Collection<Long> alreadyShown) {
        List<CompletableFuture<ShowcaseSlide>> futures = quoteService.sample(alreadyShown, PICKED).stream()
                .map(q -> CompletableFuture.supplyAsync(() -> slide(q), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).filter(s -> s != null).limit(SLIDES).toList();
    }

    private ShowcaseSlide slide(Quote quote) {
        try {
            TitleSummary title = mapper.summary(
                    tmdb.detail("/" + quote.getMediaType() + "/" + quote.getTmdbId(), TmdbQuery.create().with("language", properties.language()).build()), quote.getMediaType());
            if (title.backdropPath() == null || title.title() == null) {
                return null;
            }
            return new ShowcaseSlide(quote.getId(), quote.getTmdbId(), quote.getMediaType(), quote.getCategory().name(), title.title(), title.year(),
                    title.backdropPath(), title.posterPath(), quote.getLine(), quote.getSpeaker());
        } catch (RuntimeException ex) {
            return null; // this title is unavailable right now; the others still show
        }
    }
}
