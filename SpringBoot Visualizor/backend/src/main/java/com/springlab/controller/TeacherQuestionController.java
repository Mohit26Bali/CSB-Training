package com.springlab.controller;

import com.springlab.dto.challenge.QuestionView;
import com.springlab.dto.common.PageResponse;
import com.springlab.dto.question.ActiveRequest;
import com.springlab.dto.question.QuestionDetail;
import com.springlab.dto.question.QuestionRequest;
import com.springlab.dto.question.QuestionSummary;
import com.springlab.entity.Difficulty;
import com.springlab.service.TeacherQuestionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/teacher/questions")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherQuestionController {

    private final TeacherQuestionService questionService;

    public TeacherQuestionController(TeacherQuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public PageResponse<QuestionSummary> list(@RequestParam(required = false) Long topicId,
                                              @RequestParam(required = false) Difficulty difficulty,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "false") boolean mine,
                                              @RequestParam(required = false) String q,
                                              @PageableDefault(size = 20, sort = "updatedAt",
                                                      direction = org.springframework.data.domain.Sort.Direction.DESC)
                                              Pageable pageable,
                                              @AuthenticationPrincipal Jwt jwt) {
        return questionService.list(jwt.getSubject(), topicId, difficulty, active, mine, q, pageable);
    }

    @GetMapping("/{id}")
    public QuestionDetail get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return questionService.get(jwt.getSubject(), id);
    }

    /** The student-facing representation (no correct flags, no explanation). */
    @GetMapping("/{id}/preview")
    public QuestionView preview(@PathVariable Long id) {
        return questionService.preview(id);
    }

    @PostMapping
    public ResponseEntity<QuestionDetail> create(@Valid @RequestBody QuestionRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        QuestionDetail created = questionService.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/teacher/questions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public QuestionDetail update(@PathVariable Long id, @Valid @RequestBody QuestionRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return questionService.update(jwt.getSubject(), id, request);
    }

    @PatchMapping("/{id}/active")
    public QuestionDetail setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return questionService.setActive(jwt.getSubject(), id, request.active());
    }
}
