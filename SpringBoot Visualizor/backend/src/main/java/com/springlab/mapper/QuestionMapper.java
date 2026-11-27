package com.springlab.mapper;

import com.springlab.dto.challenge.OptionView;
import com.springlab.dto.challenge.QuestionView;
import com.springlab.dto.question.QuestionDetail;
import com.springlab.dto.question.QuestionSummary;
import com.springlab.entity.Question;

public final class QuestionMapper {

    private static final String SEED_AUTHOR = "SpringLab";

    private QuestionMapper() {
    }

    /** Student-facing: id, text and option texts only. Do not add correctness or explanation here. */
    public static QuestionView toView(Question question, int position) {
        return new QuestionView(
                question.getId(),
                position,
                question.getText(),
                question.getDifficulty(),
                question.getTopic().getName(),
                question.getTopic().getSlug(),
                question.getOptions().stream().map(o -> new OptionView(o.getId(), o.getText())).toList());
    }

    public static QuestionDetail toDetail(Question question, boolean editable) {
        return new QuestionDetail(
                question.getId(),
                question.getText(),
                question.getExplanation(),
                question.getTopic().getId(),
                question.getTopic().getName(),
                question.getTopic().getSlug(),
                question.getDifficulty(),
                question.isActive(),
                authorName(question),
                editable,
                question.getCreatedAt(),
                question.getUpdatedAt(),
                question.getVersion(),
                question.getOptions().stream()
                        .map(o -> new QuestionDetail.OptionDetail(o.getId(), o.getText(), o.isCorrect())).toList());
    }

    public static QuestionSummary toSummary(Question question, boolean editable) {
        return new QuestionSummary(
                question.getId(),
                question.getText(),
                question.getTopic().getName(),
                question.getTopic().getSlug(),
                question.getDifficulty(),
                question.isActive(),
                authorName(question),
                editable,
                question.getUpdatedAt());
    }

    private static String authorName(Question question) {
        return question.getCreatedBy() == null ? SEED_AUTHOR : question.getCreatedBy().getUsername();
    }
}
