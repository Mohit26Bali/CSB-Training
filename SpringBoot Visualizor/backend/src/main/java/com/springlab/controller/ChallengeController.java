package com.springlab.controller;

import com.springlab.dto.challenge.AttemptResult;
import com.springlab.dto.challenge.AttemptSummary;
import com.springlab.dto.challenge.AttemptView;
import com.springlab.dto.challenge.StartChallengeRequest;
import com.springlab.dto.challenge.SubmitAnswersRequest;
import com.springlab.dto.common.PageResponse;
import com.springlab.service.ChallengeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Available to every authenticated role; each user can only ever see and submit their own attempts. */
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping
    public ResponseEntity<AttemptView> start(@Valid @RequestBody(required = false) StartChallengeRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        StartChallengeRequest effective = request == null ? new StartChallengeRequest(null, null, null) : request;
        AttemptView view = challengeService.start(jwt.getSubject(), effective);
        return ResponseEntity.created(URI.create("/api/challenges/" + view.id())).body(view);
    }

    @GetMapping("/me")
    public PageResponse<AttemptSummary> history(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @AuthenticationPrincipal Jwt jwt) {
        return challengeService.history(jwt.getSubject(), page, size);
    }

    @GetMapping("/{id}")
    public AttemptView get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return challengeService.get(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/submit")
    public AttemptResult submit(@PathVariable Long id, @Valid @RequestBody SubmitAnswersRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return challengeService.submit(jwt.getSubject(), id, request);
    }

    @GetMapping("/{id}/result")
    public AttemptResult result(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return challengeService.result(jwt.getSubject(), id);
    }
}
