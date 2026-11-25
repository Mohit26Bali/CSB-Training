package com.csb.cinescope.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.Genre;
import com.csb.cinescope.dto.HomeResponse;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.PersonDetail;
import com.csb.cinescope.dto.SearchItem;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.TitleSummary;
import com.csb.cinescope.dto.WatchProviders;
import com.csb.cinescope.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The catalogue endpoints the React app uses. No logic here: parameters go to {@code service/CatalogService}, which
 * validates them (a bad value becomes a 400 through {@code exception/GlobalExceptionHandler}) and returns
 * {@code dto/} records.
 *
 * <pre>
 * GET /api/home                          hero title + all home-page rows in one request
 * GET /api/titles?category&amp;type&amp;page    one full list (trending, popular, top_rated, now_playing, upcoming, on_the_air)
 * GET /api/discover?type&amp;genre&amp;year...   filtered browsing
 * GET /api/search?query&amp;page             movies, shows and people
 * GET /api/genres?type                   genre list for the filter
 * GET /api/titles/{type}/{id}            one movie / show with cast, trailers, similar titles
 * GET /api/people/{id}                   a person with biography and filmography
 * GET /api/titles/{type}/{id}/providers?region   where to watch it (stream / free / rent / buy), default India
 * </pre>
 * All of these need a login (config/SecurityConfig), like the rest of the app apart from the sign-in screens' own data
 * (ShowcaseController). The watchlist endpoints are in WatchlistController.
 */
@Tag(name = "Catalog", description = "Movie and TV discovery backed by TMDB (login required)")
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "Home page", description = "The hero title and the rows of the home page (trending, popular, in cinemas, top rated, coming soon, TV).")
    @GetMapping("/home")
    public HomeResponse home() {
        return catalog.home();
    }

    @Operation(summary = "A full list", description = "category: trending, popular, top_rated, now_playing, upcoming (movies only), on_the_air (TV only). type: movie, tv (trending also accepts all).")
    @GetMapping("/titles")
    public PageResult<TitleSummary> list(@RequestParam(defaultValue = "popular") String category,
                                         @RequestParam(defaultValue = "movie") String type,
                                         @RequestParam(defaultValue = "1") int page) {
        return catalog.list(category, type, page);
    }

    @Operation(summary = "Browse with filters", description = "Optional genre id, release year and minimum rating; sort is popularity, rating or newest.")
    @GetMapping("/discover")
    public PageResult<TitleSummary> discover(@RequestParam(defaultValue = "movie") String type,
                                             @RequestParam(required = false) Integer genre,
                                             @RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Double minRating,
                                             @RequestParam(defaultValue = "popularity") String sort,
                                             @RequestParam(defaultValue = "1") int page) {
        return catalog.discover(type, genre, year, minRating, sort, page);
    }

    @Operation(summary = "Search", description = "Searches movies, TV shows and people at once.")
    @GetMapping("/search")
    public PageResult<SearchItem> search(@RequestParam String query, @RequestParam(defaultValue = "1") int page) {
        return catalog.search(query, page);
    }

    @Operation(summary = "Genres", description = "The genre list of movies or TV shows, for the browse filter.")
    @GetMapping("/genres")
    public List<Genre> genres(@RequestParam(defaultValue = "movie") String type) {
        return catalog.genres(type);
    }

    @Operation(summary = "Person", description = "An actor, director or other film person with biography, best-known titles and the full filmography (acting and crew).")
    @GetMapping("/people/{id}")
    public PersonDetail person(@PathVariable long id) {
        return catalog.person(id);
    }

    @Operation(summary = "Where to watch", description = "Streaming, free, rent and buy options in one country (region: IN, US ...; default India). Availability data is provided by JustWatch via TMDB.")
    @GetMapping("/titles/{type}/{id}/providers")
    public WatchProviders providers(@PathVariable String type, @PathVariable long id, @RequestParam(required = false) String region) {
        return catalog.providers(type, id, region);
    }

    @Operation(summary = "Title details", description = "One movie or TV show with facts, cast, trailers and similar titles. type is movie or tv.")
    @GetMapping("/titles/{type}/{id}")
    public TitleDetail detail(@PathVariable String type, @PathVariable long id) {
        return catalog.detail(type, id);
    }
}
