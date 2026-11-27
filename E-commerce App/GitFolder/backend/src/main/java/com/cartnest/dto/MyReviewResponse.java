package com.cartnest.dto;

public record MyReviewResponse(boolean canReview, String reason, ReviewResponse review) {
}
