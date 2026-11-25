package com.csb.cinescope.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.Recommendation;
import com.csb.cinescope.service.RecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code GET /api/tonight}: the "what should I watch tonight?" endpoint. No logic here; the wishes go to
 * {@code service/RecommendationService}.
 *
 * <p>Needs a login (config/SecurityConfig), and uses who is asking: the answer skips what the user has already watched and
 * prefers their "want to watch" titles. The frontend page is pages/TonightPage.
 */
@Tag(name = "Tonight", description = "What should I watch tonight? (login required; personalised from your watchlist)")
@RestController
@RequestMapping("/api")
public class TonightController {

    private final RecommendationService recommendations;

    public TonightController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Operation(summary = "What to watch tonight",
            description = "One pick plus alternatives. mood: laugh, thrill, mind, romance, adventure, family, scary. "
                    + "maxMinutes 30-300. language: two letters (en, hi ...). streamingOnly: only what streams in India. "
                    + "familySafe: only titles rated for all ages (U, G, PG, TV-Y, TV-G ...); unrated titles are left out. "
                    + "Send the same seed to repeat an answer, a new one for \"show me another\".")
    @GetMapping("/tonight")
    public Recommendation tonight(@RequestParam(defaultValue = "movie") String type,
                                  @RequestParam(required = false) String mood,
                                  @RequestParam(required = false) Integer maxMinutes,
                                  @RequestParam(required = false) String language,
                                  @RequestParam(defaultValue = "false") boolean streamingOnly,
                                  @RequestParam(defaultValue = "false") boolean familySafe,
                                  @RequestParam(required = false) Long seed,
                                  Authentication authentication) {
        return recommendations.tonight(authentication.getName(), type, mood, maxMinutes, language, streamingOnly, familySafe, seed);
    }
}
