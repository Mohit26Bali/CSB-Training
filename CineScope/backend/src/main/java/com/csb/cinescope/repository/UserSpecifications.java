package com.csb.cinescope.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;

import jakarta.persistence.criteria.Predicate;

/**
 * The search filters of the administrator's user list, built as one JPA {@link Specification} for
 * {@code UserRepository#findAll(Specification, Pageable)}: text in the username, email or display name (any letter case),
 * an exact role, and enabled / disabled. Any filter that is null is left out. Used by {@code service/AdminUserService#list}.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> matching(String text, Role role, Boolean enabled) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("displayName")), like, '\\')));
            }
            if (role != null) {
                all.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                all.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }
}
