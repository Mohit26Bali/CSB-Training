package com.csb.cinescope.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.EpisodeHeatmap;
import com.csb.cinescope.service.EpisodeRatingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code GET /api/titles/tv/{id}/episodes}: every episode of a show with its rating, season by season, for the heatmap on a show's page
 * (frontend components/EpisodeHeatmap). No logic here: {@code service/EpisodeRatingsService} asks TMDB. Needs a login like the rest of the
 * catalogue (config/SecurityConfig).
 */
@Tag(name = "Episodes", description = "Episode ratings of a show, season by season (login required)")
@RestController
@RequestMapping("/api")
public class EpisodeController {

    private final EpisodeRatingsService episodes;

    public EpisodeController(EpisodeRatingsService episodes) {
        this.episodes = episodes;
    }

    @Operation(summary = "Episode ratings of a show", description = "Every episode of every season (specials excluded) with TMDB's average vote, the best and worst episodes, and season averages. An episode nobody has rated has a null rating.")
    @GetMapping("/titles/tv/{id}/episodes")
    public EpisodeHeatmap heatmap(@PathVariable long id) {
        return episodes.forShow(id);
    }
}
