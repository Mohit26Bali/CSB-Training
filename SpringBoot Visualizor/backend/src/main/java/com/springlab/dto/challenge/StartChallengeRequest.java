package com.springlab.dto.challenge;

import com.springlab.entity.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** All fields are optional: no topic = every topic, no difficulty = any, no count = 10. */
public record StartChallengeRequest(
        @Size(max = 60) String topic,
        Difficulty difficulty,
        @Min(1) @Max(20) Integer count) {
}
