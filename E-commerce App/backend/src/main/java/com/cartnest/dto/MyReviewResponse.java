package com.cartnest.dto;

/** Whether the logged-in customer may review the product (and why not), and their review if they wrote one. */
public record MyReviewResponse(boolean canReview, String reason, ReviewResponse review) {
}
