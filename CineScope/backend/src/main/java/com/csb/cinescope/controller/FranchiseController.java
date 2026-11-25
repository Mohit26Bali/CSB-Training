package com.csb.cinescope.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.FranchiseAddResult;
import com.csb.cinescope.dto.FranchiseView;
import com.csb.cinescope.service.FranchiseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The franchise tracker. No logic here: {@code service/FranchiseService} asks TMDB for the series and reads the person's
 * watchlist. Everything needs a login (config/SecurityConfig).
 *
 * <pre>
 * GET  /api/titles/movie/{id}/franchise   the series the film belongs to and the person's progress (204 if it is in no series)
 * POST /api/collections/{id}/watchlist    put the released films of the series that are not on the list on it
 * </pre>
 * The frontend section is components/Franchise on pages/TitlePage.
 */
@Tag(name = "Franchises", description = "Film series and your progress through them (login required)")
@RestController
@RequestMapping("/api")
public class FranchiseController {

    private final FranchiseService franchises;

    public FranchiseController(FranchiseService franchises) {
        this.franchises = franchises;
    }

    @Operation(summary = "The series a film belongs to", description = "The films of the series, oldest first, with your status on each and how many you have watched. 204 when the film is not part of a series.")
    @GetMapping("/titles/movie/{id}/franchise")
    public ResponseEntity<FranchiseView> franchise(@PathVariable long id, Authentication authentication) {
        return franchises.ofFilm(authentication.getName(), id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Add the rest of a series to my watchlist", description = "Adds every released film of the collection that is not already on your list, as want-to-watch. Titles already on the list are never changed.")
    @PostMapping("/collections/{id}/watchlist")
    public FranchiseAddResult addRest(@PathVariable long id, Authentication authentication) {
        return franchises.addRest(authentication.getName(), id);
    }
}
