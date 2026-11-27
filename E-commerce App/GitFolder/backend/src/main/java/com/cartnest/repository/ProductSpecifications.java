package com.cartnest.repository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.cartnest.entity.Product;
import com.cartnest.entity.ProductVariant;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class ProductSpecifications {
    private ProductSpecifications() {
    }

    public static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Product> matchesText(String text) {
        List<String> words = words(text);
        return (root, query, cb) -> {
            Join<Object, Object> brand = root.join("brand", JoinType.LEFT);
            Join<Object, Object> category = root.join("category", JoinType.INNER);
            Join<Object, Object> parent = category.join("parent", JoinType.LEFT);
            Predicate[] perWord = words.stream().map(word -> {
                String pattern = "%" + word + "%";
                Subquery<Integer> tag = query.subquery(Integer.class);
                Root<Product> tagged = tag.correlate(root);
                Join<Product, String> tags = tagged.join("tags");
                tag.select(cb.literal(1)).where(cb.like(cb.lower(tags), pattern));
                return cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(brand.get("name")), pattern),
                        cb.like(cb.lower(category.get("name")), pattern),
                        cb.like(cb.lower(parent.get("name")), pattern),
                        cb.exists(tag));
            }).toArray(Predicate[]::new);
            return cb.and(perWord);
        };
    }

    public static Specification<Product> brandSlugs(Collection<String> slugs) {
        return (root, query, cb) -> root.join("brand").get("slug").in(slugs);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> ratingAtLeast(double rating) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ratingAvg"), BigDecimal.valueOf(rating));
    }

    public static Specification<Product> discountAtLeast(int percent) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("discountPercent"), percent);
    }

    public static Specification<Product> availableInSizes(Collection<String> labels) {
        return (root, query, cb) -> cb.exists(variantSubquery(root, query.subquery(Integer.class), cb,
                (variant) -> cb.and(variant.get("label").in(labels), cb.greaterThan(variant.get("stock"), 0))));
    }

    public static Specification<Product> inStock() {
        return (root, query, cb) -> cb.exists(variantSubquery(root, query.subquery(Integer.class), cb,
                (variant) -> cb.greaterThan(variant.get("stock"), 0)));
    }

    public static Specification<Product> orderedByRelevance(String text) {
        List<String> words = words(text);
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                Join<Object, Object> brand = root.join("brand", JoinType.LEFT);
                Join<Object, Object> category = root.join("category", JoinType.INNER);
                Expression<Integer> score = cb.literal(0);
                for (String word : words) {
                    String pattern = "%" + word + "%";
                    Subquery<Integer> tag = query.subquery(Integer.class);
                    Join<Product, String> tags = tag.correlate(root).join("tags");
                    tag.select(cb.literal(1)).where(cb.like(cb.lower(tags), pattern));
                    score = cb.sum(score, points(cb, cb.like(cb.lower(root.get("title")), pattern), 3));
                    score = cb.sum(score, points(cb, cb.like(cb.lower(category.get("name")), pattern), 2));
                    score = cb.sum(score, points(cb, cb.like(cb.lower(brand.get("name")), pattern), 2));
                    score = cb.sum(score, points(cb, cb.exists(tag), 1));
                }
                query.orderBy(cb.desc(score), cb.desc(root.get("ratingCount")), cb.asc(root.get("id")));
            }
            return null;
        };
    }

    private static Expression<Integer> points(CriteriaBuilder cb, Predicate matches, int points) {
        return cb.<Integer>selectCase().when(matches, points).otherwise(0);
    }

    private interface VariantCondition {
        Predicate on(Root<ProductVariant> variant);
    }

    private static Subquery<Integer> variantSubquery(Root<Product> product, Subquery<Integer> subquery,
                                                     CriteriaBuilder cb, VariantCondition condition) {
        Root<ProductVariant> variant = subquery.from(ProductVariant.class);
        return subquery.select(cb.literal(1))
                .where(cb.equal(variant.get("product"), product), condition.on(variant));
    }

    private static List<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(w -> w.replace("%", "").replace("_", ""))
                .filter(w -> !w.isEmpty())
                .toList();
    }
}
