package com.springlab.service;

import com.springlab.dto.challenge.QuestionView;
import com.springlab.dto.common.PageResponse;
import com.springlab.dto.question.QuestionDetail;
import com.springlab.dto.question.QuestionRequest;
import com.springlab.dto.question.QuestionSummary;
import com.springlab.entity.Difficulty;
import com.springlab.entity.Question;
import com.springlab.entity.QuestionOption;
import com.springlab.entity.Role;
import com.springlab.entity.Topic;
import com.springlab.entity.User;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.exception.InvalidRequestException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.mapper.QuestionMapper;
import com.springlab.repository.QuestionRepository;
import com.springlab.repository.TopicRepository;
import com.springlab.repository.UserRepository;
import com.springlab.util.SortValidator;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Question bank management for teachers and admins. Role access to the endpoints is enforced in the web layer;
 * ownership is enforced here: a teacher may modify only questions they authored, an admin may modify any, and
 * seed questions (no author) are admin-only. Everyone with teacher access may read every question.
 */
@Service
public class TeacherQuestionService {

    private static final Set<String> SORTABLE = Set.of("id", "difficulty", "active", "createdAt", "updatedAt");

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    public TeacherQuestionService(QuestionRepository questionRepository, TopicRepository topicRepository,
                                  UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummary> list(String username, Long topicId, Difficulty difficulty, Boolean active,
                                              boolean mineOnly, String text, Pageable pageable) {
        SortValidator.requireAllowed(pageable, SORTABLE);
        User actor = actor(username);
        String normalizedText = (text == null || text.isBlank()) ? null : text.trim();
        return PageResponse.from(questionRepository
                .search(topicId, difficulty, active, mineOnly ? actor.getId() : null, normalizedText, pageable)
                .map(q -> QuestionMapper.toSummary(q, canModify(actor, q))));
    }

    @Transactional(readOnly = true)
    public QuestionDetail get(String username, Long id) {
        User actor = actor(username);
        Question question = load(id);
        return QuestionMapper.toDetail(question, canModify(actor, question));
    }

    /** Exactly the object a student would receive, so teachers can check what learners will see. */
    @Transactional(readOnly = true)
    public QuestionView preview(Long id) {
        return QuestionMapper.toView(load(id), 0);
    }

    @Transactional
    public QuestionDetail create(String username, QuestionRequest request) {
        User actor = actor(username);
        validateOptions(request);
        Question question = new Question(topic(request.topicId()), request.difficulty(), request.text().trim(),
                request.explanation().trim(), actor);
        int position = 0;
        for (QuestionRequest.OptionInput input : request.options()) {
            question.addOption(input.text().trim(), input.correct(), position++);
        }
        Question saved = questionRepository.saveAndFlush(question);
        return QuestionMapper.toDetail(saved, true);
    }

    @Transactional
    public QuestionDetail update(String username, Long id, QuestionRequest request) {
        User actor = actor(username);
        Question question = load(id);
        requireCanModify(actor, question);
        if (request.version() == null) {
            throw new InvalidRequestException("version is required when updating a question");
        }
        if (!request.version().equals(question.getVersion())) {
            throw new BusinessRuleViolationException(
                    "This question was modified by someone else. Reload it and apply your changes again");
        }
        validateOptions(request);

        Map<Long, QuestionOption> existing = new HashMap<>();
        question.getOptions().forEach(o -> existing.put(o.getId(), o));
        Set<Long> keep = new HashSet<>();
        for (QuestionRequest.OptionInput input : request.options()) {
            if (input.id() != null) {
                if (!existing.containsKey(input.id())) {
                    throw new InvalidRequestException("Option " + input.id() + " does not belong to this question");
                }
                if (!keep.add(input.id())) {
                    throw new InvalidRequestException("Option " + input.id() + " is listed more than once");
                }
            }
        }

        question.updateContent(topic(request.topicId()), request.difficulty(), request.text().trim(),
                request.explanation().trim());
        question.touch(); // guarantees a version bump even when only the options changed
        // Existing options are edited in place (past answers keep pointing at them); omitted ones are removed.
        question.removeOptionsNotIn(keep);
        int position = 0;
        for (QuestionRequest.OptionInput input : request.options()) {
            if (input.id() != null) {
                existing.get(input.id()).update(input.text().trim(), input.correct(), position);
            } else {
                question.addOption(input.text().trim(), input.correct(), position);
            }
            position++;
        }
        Question saved = questionRepository.saveAndFlush(question);
        return QuestionMapper.toDetail(saved, true);
    }

    /** Deactivation is the only way to retire a question; it keeps every past attempt readable. */
    @Transactional
    public QuestionDetail setActive(String username, Long id, boolean active) {
        User actor = actor(username);
        Question question = load(id);
        requireCanModify(actor, question);
        question.setActive(active);
        Question saved = questionRepository.saveAndFlush(question);
        return QuestionMapper.toDetail(saved, true);
    }

    private void validateOptions(QuestionRequest request) {
        // The DTO already checks size, blank texts and "exactly one correct"; these checks need normalized text.
        Set<String> texts = new HashSet<>();
        for (QuestionRequest.OptionInput option : request.options()) {
            if (!texts.add(option.text().trim().toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Option texts must be unique within a question");
            }
        }
    }

    private Topic topic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new InvalidRequestException("Unknown topic id " + topicId));
    }

    private Question load(Long id) {
        return questionRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }

    private User actor(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }

    private static boolean canModify(User actor, Question question) {
        return actor.getRole() == Role.ADMIN || question.isOwnedBy(actor);
    }

    private static void requireCanModify(User actor, Question question) {
        if (!canModify(actor, question)) {
            // Thrown from the service; propagates to Spring Security, which answers 403 for an authenticated user.
            throw new AccessDeniedException("Only the author or an administrator can modify this question");
        }
    }
}
