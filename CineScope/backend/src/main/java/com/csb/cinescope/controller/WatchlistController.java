package com.csb.cinescope.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.WatchlistEntry;
import com.csb.cinescope.dto.WatchlistEntryRequest;
import com.csb.cinescope.dto.WatchlistSummary;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.service.WatchlistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The logged-in user's watchlist. Every endpoint requires a token ({@code config/SecurityConfig}) and works only on the
 * caller's own list: the username comes from the token, never from the URL.
 *
 * <pre>
 * GET    /api/watchlist                  list, with ?status= &amp;type= &amp;sort=added|updated|title|rating &amp;page= &amp;size=
 * GET    /api/watchlist/summary          counts per status
 * GET    /api/watchlist/{type}/{id}      the entry for one title (404 when it is not on the list)
 * PUT    /api/watchlist/{type}/{id}      add the title, or change its status / rating / note
 * DELETE /api/watchlist/{type}/{id}      remove it
 * </pre>
 * {@code type} is movie or tv; {@code id} is the TMDB id, the same identity the catalogue endpoints use. Logic lives in
 * {@code service/WatchlistService}.
 */
@Tag(name = "Watchlist", description = "Your personal watchlist (login required)")
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlist;

    public WatchlistController(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    @Operation(summary = "List my watchlist", description = "Filter by status (WANT, WATCHING, WATCHED) and type; sort by added (newest first), updated, title or rating.")
    @GetMapping
    public PageResult<WatchlistEntry> list(@RequestParam(required = false) WatchStatus status,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(defaultValue = "added") String sort,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           Authentication authentication) {
        return watchlist.list(authentication.getName(), status, type, sort, page, size);
    }

    @Operation(summary = "Counts per status")
    @GetMapping("/summary")
    public WatchlistSummary summary(Authentication authentication) {
        return watchlist.summary(authentication.getName());
    }

    @Operation(summary = "One title's entry", description = "404 when the title is not on your list.")
    @GetMapping("/{type}/{id}")
    public WatchlistEntry get(@PathVariable String type, @PathVariable long id, Authentication authentication) {
        return watchlist.get(authentication.getName(), type, id);
    }

    @Operation(summary = "Add a title or change it",
            description = "Creates the entry (201) or replaces status, rating and note of an existing one (200). A rating (1-10) needs status WATCHED. "
                    + "The title must exist at TMDB.")
    @PutMapping("/{type}/{id}")
    public ResponseEntity<WatchlistEntry> save(@PathVariable String type, @PathVariable long id,
                                               @Valid @RequestBody WatchlistEntryRequest request, Authentication authentication) {
        WatchlistService.SaveResult result = watchlist.save(authentication.getName(), type, id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.entry());
    }

    @Operation(summary = "Remove a title", description = "404 when the title is not on your list.")
    @DeleteMapping("/{type}/{id}")
    public ResponseEntity<Void> remove(@PathVariable String type, @PathVariable long id, Authentication authentication) {
        watchlist.remove(authentication.getName(), type, id);
        return ResponseEntity.noContent().build();
    }
}
