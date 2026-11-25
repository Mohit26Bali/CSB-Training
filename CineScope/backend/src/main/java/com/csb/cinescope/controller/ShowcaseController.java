package com.csb.cinescope.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.ShowcaseSlide;
import com.csb.cinescope.service.ShowcaseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code GET /api/showcase}: the films and famous lines for the sign-in and sign-up screens. The only catalogue endpoint
 * that is open without a login (config/SecurityConfig), because those screens are what a visitor sees first. No logic here:
 * {@code service/ShowcaseService} builds the slides; the frontend's ShowcaseCarousel shows them.
 */
@Tag(name = "Showcase", description = "Pictures and famous lines for the sign-in screens (public)")
@SecurityRequirements
@RestController
@RequestMapping("/api")
public class ShowcaseController {

    private final ShowcaseService showcase;

    public ShowcaseController(ShowcaseService showcase) {
        this.showcase = showcase;
    }

    /** The most remembered ids honoured; a longer list is cut, so a request cannot make the server do unbounded work. */
    static final int MAX_EXCLUDED = 500;

    @Operation(summary = "Films and shows with famous lines", description = "A varied set of titles from all categories: backdrop, poster, title, year, a famous line and who says it, for the sign-in screens. Pass the quote ids already shown as exclude (comma separated) to get different ones. Titles TMDB cannot supply are left out.")
    @GetMapping("/showcase")
    public List<ShowcaseSlide> slides(@RequestParam(name = "exclude", defaultValue = "") List<Long> exclude) {
        return showcase.slides(exclude.size() > MAX_EXCLUDED ? exclude.subList(exclude.size() - MAX_EXCLUDED, exclude.size()) : exclude);
    }
}
