package com.springlab.coding.web;

import com.springlab.coding.dto.CodingDtos;
import com.springlab.coding.submission.CodingService;
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
import java.util.List;

/**
 * Coding practice, for every authenticated role. Each user sees and runs only their own submissions and progress. The learner sends source
 * code and nothing else: the problem is the slug in the URL, and the tests, limits and verdict logic are the server's.
 */
@RestController
@RequestMapping("/api/coding")
public class CodingController {

    private final CodingService coding;

    public CodingController(CodingService coding) {
        this.coding = coding;
    }

    @GetMapping("/topics")
    public List<CodingDtos.TopicResponse> topics() {
        return coding.topics();
    }

    @GetMapping("/problems")
    public List<CodingDtos.ProblemSummary> problems(@RequestParam(required = false) String topic, @RequestParam(required = false) String difficulty,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return coding.list(jwt.getSubject(), blankToNull(topic), blankToNull(difficulty));
    }

    @GetMapping("/problems/{slug}")
    public CodingDtos.ProblemDetail problem(@PathVariable String slug) {
        return coding.detail(slug);
    }

    @PostMapping("/problems/{slug}/run")
    public CodingDtos.RunResponse run(@PathVariable String slug, @Valid @RequestBody CodingDtos.SourceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return coding.run(jwt.getSubject(), slug, request.sourceCode());
    }

    @PostMapping("/problems/{slug}/submit")
    public ResponseEntity<CodingDtos.SubmissionResponse> submit(@PathVariable String slug, @Valid @RequestBody CodingDtos.SourceRequest request,
                                                                @AuthenticationPrincipal Jwt jwt) {
        CodingDtos.SubmissionResponse response = coding.submit(jwt.getSubject(), slug, request.sourceCode());
        return ResponseEntity.created(URI.create("/api/coding/submissions/" + response.id())).body(response);
    }

    @GetMapping("/problems/{slug}/submissions")
    public List<CodingDtos.SubmissionSummary> history(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return coding.history(jwt.getSubject(), slug);
    }

    @GetMapping("/submissions/{id}")
    public CodingDtos.SubmissionDetail submission(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return coding.submission(jwt.getSubject(), id);
    }

    @GetMapping("/progress/me")
    public CodingDtos.ProgressResponse progress(@AuthenticationPrincipal Jwt jwt) {
        return coding.progress(jwt.getSubject());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
