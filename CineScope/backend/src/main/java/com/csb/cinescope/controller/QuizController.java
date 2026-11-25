package com.csb.cinescope.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.QuizAnswerRequest;
import com.csb.cinescope.dto.QuizAnswerResult;
import com.csb.cinescope.dto.QuizLeaderboard;
import com.csb.cinescope.dto.QuizRoundView;
import com.csb.cinescope.dto.QuizStartRequest;
import com.csb.cinescope.service.QuizService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The famous-lines quiz. No logic here: {@code service/QuizService} applies the rules. Everything needs a login
 * (config/SecurityConfig); the player is the one named by the token.
 *
 * <pre>
 * POST /api/quiz/rounds                  start a round in a category (or the CAST / POSTER games): ten questions, no answers
 * POST /api/quiz/rounds/{id}/questions/{seq}/shown   the page reports a question is on screen (starts the poster game's speed clock)
 * POST /api/quiz/rounds/{id}/answers     answer one question; the reply says right or wrong and reveals the truth
 * GET  /api/quiz/leaderboard?category=   the ten best scores in a category and the caller's own place
 * </pre>
 * The frontend is pages/QuizPage.
 */
@Tag(name = "Quiz", description = "Guess the film, show or speaker from a famous line (login required)")
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quiz;

    public QuizController(QuizService quiz) {
        this.quiz = quiz;
    }

    @Operation(summary = "Start a round", description = "Ten questions. category: ALL, HOLLYWOOD, TV, BOLLYWOOD or ANIME (famous lines you have not been shown before), CAST (which film or show has these actors) or POSTER (name the blurred poster). The correct answers are not included.")
    @PostMapping("/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizRoundView start(@Valid @RequestBody QuizStartRequest request, Authentication authentication) {
        return quiz.start(authentication.getName(), request.category());
    }

    @Operation(summary = "A question is on screen", description = "Tells the server a question has reached the player's screen. In the poster game a right answer within a few seconds of this earns a speed bonus, measured on the server; only the first report counts. Other kinds ignore it.")
    @PostMapping("/rounds/{id}/questions/{seq}/shown")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shown(@PathVariable long id, @PathVariable int seq, Authentication authentication) {
        quiz.shown(authentication.getName(), id, seq);
    }

    @Operation(summary = "Answer a question", description = "choice is the index (0-3) of the option picked. A question can be answered once. The reply reveals the right option and where the quote is from.")
    @PostMapping("/rounds/{id}/answers")
    public QuizAnswerResult answer(@PathVariable long id, @Valid @RequestBody QuizAnswerRequest request, Authentication authentication) {
        return quiz.answer(authentication.getName(), id, request);
    }

    @Operation(summary = "Leaderboard", description = "Each person's best finished round in the category; the top ten and your own place.")
    @GetMapping("/leaderboard")
    public QuizLeaderboard leaderboard(@RequestParam(defaultValue = "ALL") String category, Authentication authentication) {
        return quiz.leaderboard(authentication.getName(), category);
    }
}
