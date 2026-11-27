package com.springlab.controller;

import com.springlab.dto.progress.LeaderboardEntry;
import com.springlab.dto.progress.ProgressResponse;
import com.springlab.service.ProgressService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/progress/me")
    public ProgressResponse myProgress(@AuthenticationPrincipal Jwt jwt) {
        return progressService.progressFor(jwt.getSubject());
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard(@RequestParam(required = false) String topic,
                                              @RequestParam(defaultValue = "10") int limit) {
        return progressService.leaderboard(topic, limit);
    }
}
