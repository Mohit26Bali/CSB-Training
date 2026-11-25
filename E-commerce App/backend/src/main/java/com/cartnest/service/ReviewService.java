package com.cartnest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.MyReviewResponse;
import com.cartnest.dto.ReviewRequest;
import com.cartnest.dto.ReviewResponse;
import com.cartnest.entity.Product;
import com.cartnest.entity.Review;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ForbiddenException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.OrderItemRepository;
import com.cartnest.repository.ProductRepository;
import com.cartnest.repository.ReviewRepository;
import com.cartnest.repository.UserRepository;

/**
 * Customer reviews. Only customers who received the product (a delivered order) may review it, once per product.
 * Each new review recalculates the product's average rating and review count.
 */
@Service
public class ReviewService {

    private static final String NOT_RECEIVED = "You can review a product once it has been delivered to you";

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductMapper mapper;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
                         OrderItemRepository orderItemRepository, UserRepository userRepository, ProductMapper mapper) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    /** For the product page: may this customer write a review, and what did they write already? */
    @Transactional(readOnly = true)
    public MyReviewResponse mine(Long userId, String slug) {
        Product product = findActive(slug);
        return reviewRepository.findByProductIdAndUserId(product.getId(), userId)
                .map(r -> new MyReviewResponse(false, "You have already reviewed this product", mapper.toReview(r)))
                .orElseGet(() -> orderItemRepository.wasDelivered(userId, product.getId())
                        ? new MyReviewResponse(true, null, null)
                        : new MyReviewResponse(false, NOT_RECEIVED, null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse write(Long userId, String slug, ReviewRequest request) {
        Product product = findActive(slug);
        if (!orderItemRepository.wasDelivered(userId, product.getId())) {
            throw new ForbiddenException(NOT_RECEIVED);
        }
        if (reviewRepository.findByProductIdAndUserId(product.getId(), userId).isPresent()) {
            throw new ConflictException("You have already reviewed this product");
        }
        Review review;
        try {
            review = reviewRepository.saveAndFlush(new Review(product, userRepository.getReferenceById(userId),
                    request.rating(), request.comment().trim()));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("You have already reviewed this product");
        }

        // Lock the product, then recount: a parallel review waits here and then sees this one
        Product locked = productRepository.lockById(product.getId()).orElseThrow();
        double average = reviewRepository.averageRating(product.getId());
        locked.setRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                (int) reviewRepository.countByProductId(product.getId()));
        return mapper.toReview(reviewRepository.findByProductIdAndUserId(product.getId(), userId).orElse(review));
    }

    private Product findActive(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product '" + slug + "' not found"));
    }
}
