package com.springlab.service;

import com.springlab.dto.challenge.AttemptView;
import com.springlab.dto.challenge.StartChallengeRequest;
import com.springlab.dto.challenge.SubmitAnswersRequest;
import com.springlab.entity.AttemptStatus;
import com.springlab.entity.Difficulty;
import com.springlab.entity.Question;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.repository.AttemptRepository;
import com.springlab.repository.QuestionRepository;
import com.springlab.repository.TopicRepository;
import com.springlab.repository.UserRepository;
import com.springlab.support.AbstractSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately NOT @Transactional: two threads must run real, separate transactions against MySQL to prove the
 * row lock on the attempt really prevents a double submission. It creates and removes only its own rows, so the
 * seed questions in the test schema are never touched.
 */
class ChallengeConcurrencyIntegrationTest extends AbstractSpringBootTest {

    @Autowired
    private ChallengeService challengeService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private AttemptRepository attemptRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User student;
    private Question question;

    @BeforeEach
    void createData() {
        student = userRepository.saveAndFlush(new User("racer", "racer@example.com", "{noop}x", Role.STUDENT));
        Question q = new Question(topicRepository.findBySlug("java-core").orElseThrow(), Difficulty.EASY,
                "concurrency question", "explanation", null);
        q.addOption("wrong", false, 0);
        q.addOption("right", true, 1);
        question = questionRepository.saveAndFlush(q);
    }

    @AfterEach
    void removeOwnData() {
        // Plain SQL: entity navigation would need a transaction, and only this test's own rows are removed.
        jdbc.update("delete from attempt_answers where attempt_id in (select id from attempts where user_id = ?)",
                student.getId());
        jdbc.update("delete from attempts where user_id = ?", student.getId());
        questionRepository.deleteById(question.getId());
        userRepository.deleteById(student.getId());
    }

    @Test
    void simultaneousSubmissionsOfTheSameAttemptSucceedExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 4; round++) {
                // Start an attempt limited to our one question so the answer payload is known.
                AttemptView attempt = challengeService.start("racer", new StartChallengeRequest("java-core",
                        Difficulty.EASY, 20));
                var q = attempt.questions().stream().filter(v -> v.id().equals(question.getId())).findFirst();
                assertThat(q).isPresent();
                long optionId = q.get().options().get(1).id();
                SubmitAnswersRequest request = new SubmitAnswersRequest(List.of(
                        new SubmitAnswersRequest.AnswerSubmission(question.getId(), optionId)));

                CyclicBarrier startTogether = new CyclicBarrier(4);
                List<Callable<Boolean>> tasks = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    tasks.add(() -> {
                        startTogether.await(10, TimeUnit.SECONDS);
                        try {
                            challengeService.submit("racer", attempt.id(), request);
                            return true;
                        } catch (BusinessRuleViolationException alreadySubmitted) {
                            return false;
                        }
                    });
                }
                long successes = 0;
                for (Future<Boolean> result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                    if (result.get()) {
                        successes++;
                    }
                }

                assertThat(successes).as("round %d: exactly one submission may win", round).isEqualTo(1);
                var stored = attemptRepository.findById(attempt.id()).orElseThrow();
                assertThat(stored.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
                assertThat(stored.getScore()).isGreaterThanOrEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
