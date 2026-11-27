package com.springlab.service;

import com.springlab.dto.challenge.AttemptResult;
import com.springlab.dto.challenge.AttemptSummary;
import com.springlab.dto.challenge.AttemptView;
import com.springlab.dto.challenge.StartChallengeRequest;
import com.springlab.dto.challenge.SubmitAnswersRequest;
import com.springlab.dto.common.PageResponse;
import com.springlab.entity.Attempt;
import com.springlab.entity.AttemptAnswer;
import com.springlab.entity.AttemptStatus;
import com.springlab.entity.Question;
import com.springlab.entity.QuestionOption;
import com.springlab.entity.Topic;
import com.springlab.entity.User;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.exception.InvalidRequestException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.mapper.AttemptMapper;
import com.springlab.repository.AttemptAnswerRepository;
import com.springlab.repository.AttemptRepository;
import com.springlab.repository.QuestionRepository;
import com.springlab.repository.TopicRepository;
import com.springlab.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChallengeService {

    static final int DEFAULT_QUESTION_COUNT = 10;

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;

    public ChallengeService(UserRepository userRepository, TopicRepository topicRepository,
                            QuestionRepository questionRepository, AttemptRepository attemptRepository,
                            AttemptAnswerRepository attemptAnswerRepository) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
    }

    @Transactional
    public AttemptView start(String username, StartChallengeRequest request) {
        User user = currentUser(username);
        Topic topic = null;
        if (request.topic() != null && !request.topic().isBlank()) {
            topic = topicRepository.findBySlug(request.topic().trim())
                    .orElseThrow(() -> new InvalidRequestException("Unknown topic '" + request.topic() + "'"));
        }
        int count = request.count() == null ? DEFAULT_QUESTION_COUNT : request.count();

        List<Long> candidateIds = new ArrayList<>(
                questionRepository.findActiveIds(topic == null ? null : topic.getId(), request.difficulty()));
        if (candidateIds.isEmpty()) {
            throw new BusinessRuleViolationException("No active questions match the selected topic and difficulty");
        }
        Collections.shuffle(candidateIds);
        List<Long> chosenIds = candidateIds.subList(0, Math.min(count, candidateIds.size()));

        Map<Long, Question> loaded = questionRepository.findAllWithOptionsByIdIn(chosenIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<Question> questions = chosenIds.stream().map(loaded::get).toList(); // keep the shuffled order

        Attempt attempt = attemptRepository.save(new Attempt(user, topic, request.difficulty(), questions));
        return AttemptMapper.toView(attempt, attempt.getAnswers());
    }

    @Transactional(readOnly = true)
    public AttemptView get(String username, Long attemptId) {
        Attempt attempt = ownedAttempt(username, attemptId);
        return AttemptMapper.toView(attempt, loadAnswers(attemptId));
    }

    @Transactional
    public AttemptResult submit(String username, Long attemptId, SubmitAnswersRequest request) {
        User user = currentUser(username);
        // Row lock: concurrent submissions of the same attempt are serialized, the second one fails below.
        Attempt attempt = attemptRepository.findOwnedForUpdate(attemptId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Challenge not found"));
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException("This challenge has already been submitted");
        }

        List<AttemptAnswer> answers = loadAnswers(attemptId);
        Map<Long, Question> questionsById = answers.stream().map(AttemptAnswer::getQuestion)
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        // Validate EVERYTHING before changing anything: an invalid submission must leave the attempt untouched.
        Map<Long, QuestionOption> selected = new HashMap<>();
        Set<Long> seenQuestions = new HashSet<>();
        for (SubmitAnswersRequest.AnswerSubmission submission : request.answers()) {
            Question question = questionsById.get(submission.questionId());
            if (question == null) {
                throw new InvalidRequestException(
                        "Question " + submission.questionId() + " is not part of this challenge");
            }
            if (!seenQuestions.add(question.getId())) {
                throw new InvalidRequestException("Question " + question.getId() + " was answered more than once");
            }
            QuestionOption option = question.getOptions().stream()
                    .filter(o -> o.getId().equals(submission.optionId())).findFirst()
                    .orElseThrow(() -> new InvalidRequestException("Option " + submission.optionId()
                            + " does not belong to question " + question.getId()));
            selected.put(question.getId(), option);
        }

        attempt.submit(selected); // managed entities: persisted by dirty checking at commit
        return AttemptMapper.toResult(attempt, answers);
    }

    /** Correct answers and explanations are only available once the attempt has been submitted. */
    @Transactional(readOnly = true)
    public AttemptResult result(String username, Long attemptId) {
        Attempt attempt = ownedAttempt(username, attemptId);
        if (attempt.getStatus() != AttemptStatus.SUBMITTED) {
            throw new BusinessRuleViolationException("Results are available only after the challenge is submitted");
        }
        return AttemptMapper.toResult(attempt, loadAnswers(attemptId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptSummary> history(String username, int page, int size) {
        User user = currentUser(username);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(attemptRepository.findByUserId(user.getId(), pageable).map(AttemptMapper::toSummary));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }

    private Attempt ownedAttempt(String username, Long attemptId) {
        User user = currentUser(username);
        return attemptRepository.findByIdAndUserId(attemptId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Challenge not found"));
    }

    /** Two queries total: answers with question/topic/selected option, then the questions' options in one go. */
    private List<AttemptAnswer> loadAnswers(Long attemptId) {
        List<AttemptAnswer> answers = attemptAnswerRepository.findDetailedByAttemptId(attemptId);
        List<Long> questionIds = answers.stream().map(a -> a.getQuestion().getId()).toList();
        if (!questionIds.isEmpty()) {
            // The fetch join initializes the options collection of the already-managed Question instances.
            questionRepository.findAllWithOptionsByIdIn(questionIds);
        }
        return answers;
    }
}
