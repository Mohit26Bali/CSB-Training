package com.springlab.dto.challenge;

public record TopicResponse(Long id, String name, String slug, String description, long activeQuestionCount) {
}
