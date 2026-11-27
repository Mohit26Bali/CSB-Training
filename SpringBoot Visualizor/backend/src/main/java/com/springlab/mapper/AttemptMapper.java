package com.springlab.mapper;

import com.springlab.dto.challenge.AttemptResult;
import com.springlab.dto.challenge.AttemptSummary;
import com.springlab.dto.challenge.AttemptView;
import com.springlab.dto.challenge.QuestionView;
import com.springlab.entity.Attempt;
import com.springlab.entity.AttemptAnswer;
import com.springlab.entity.Question;
import com.springlab.entity.QuestionOption;

import java.util.List;

public final class AttemptMapper {

    private AttemptMapper() {
    }

    public static AttemptView toView(Attempt attempt, List<AttemptAnswer> answers) {
        List<QuestionView> questions = answers.stream()
                .map(answer -> QuestionMapper.toView(answer.getQuestion(), answer.getPosition()))
                .toList();
        return new AttemptView(
                attempt.getId(),
                attempt.getStatus(),
                attempt.getTopic() == null ? null : attempt.getTopic().getName(),
                attempt.getTopic() == null ? null : attempt.getTopic().getSlug(),
                attempt.getDifficulty(),
                attempt.getTotalQuestions(),
                attempt.getStartedAt(),
                questions);
    }

    public static AttemptResult toResult(Attempt attempt, List<AttemptAnswer> answers) {
        List<AttemptResult.QuestionResult> questions = answers.stream().map(AttemptMapper::toQuestionResult).toList();
        int score = attempt.getScore() == null ? 0 : attempt.getScore();
        double percentage = attempt.getTotalQuestions() == 0 ? 0 : round1(100.0 * score / attempt.getTotalQuestions());
        return new AttemptResult(
                attempt.getId(),
                attempt.getTopic() == null ? null : attempt.getTopic().getName(),
                attempt.getTopic() == null ? null : attempt.getTopic().getSlug(),
                attempt.getDifficulty(),
                score,
                attempt.getTotalQuestions(),
                percentage,
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                questions);
    }

    public static AttemptSummary toSummary(Attempt attempt) {
        return new AttemptSummary(
                attempt.getId(),
                attempt.getStatus(),
                attempt.getTopic() == null ? null : attempt.getTopic().getName(),
                attempt.getTopic() == null ? null : attempt.getTopic().getSlug(),
                attempt.getDifficulty(),
                attempt.getTotalQuestions(),
                attempt.getScore(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt());
    }

    private static AttemptResult.QuestionResult toQuestionResult(AttemptAnswer answer) {
        Question question = answer.getQuestion();
        QuestionOption selected = answer.getSelectedOption();
        Long selectedId = selected == null ? null : selected.getId();
        List<AttemptResult.OptionResult> options = question.getOptions().stream()
                .map(o -> new AttemptResult.OptionResult(o.getId(), o.getText(), o.isCorrect(), o.getId().equals(selectedId)))
                .toList();
        return new AttemptResult.QuestionResult(
                question.getId(),
                answer.getPosition(),
                question.getText(),
                question.getDifficulty(),
                question.getTopic().getName(),
                options,
                selectedId,
                selected != null,
                Boolean.TRUE.equals(answer.getCorrect()),
                question.getExplanation());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
