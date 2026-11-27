package com.icc.incident.repository;

import com.icc.common.jwt.JwtClaims;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentState;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds the WHERE clause of the incident list. Only query building, no rules. */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> matching(IncidentScope scope, IncidentState state,
                                                   String text, JwtClaims caller) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();

            switch (scope) {
                case MINE -> conditions.add(cb.equal(root.get("reporterId"), caller.userId()));
                case ASSIGNED -> {
                    Predicate assignedToMe = cb.equal(root.get("assignedToId"), caller.userId());
                    if (caller.teamId() != null) {
                        Predicate inMyQueue = cb.and(
                                cb.isNull(root.get("assignedToId")),
                                cb.equal(root.get("assignedTeamId"), caller.teamId()));
                        conditions.add(cb.or(assignedToMe, inMyQueue));
                    } else {
                        conditions.add(assignedToMe);
                    }
                }
                case ALL -> {
                    // no condition: every incident
                }
            }

            if (state != null) {
                conditions.add(cb.equal(root.get("state"), state));
            }
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase() + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("number")), like),
                        cb.like(cb.lower(root.get("shortDescription")), like)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
