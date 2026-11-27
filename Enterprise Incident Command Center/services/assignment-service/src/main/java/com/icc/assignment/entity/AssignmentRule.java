package com.icc.assignment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assignment_rule")
public class AssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;
    private Long teamId;
    private boolean active;

    protected AssignmentRule() {
    }

    public AssignmentRule(Long id, String category, Long teamId, boolean active) {
        this.id = id;
        this.category = category;
        this.teamId = teamId;
        this.active = active;
    }

    /** The only change allowed on a rule: send the category to another team. */
    public void changeTeam(Long teamId) {
        this.teamId = teamId;
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public Long getTeamId() {
        return teamId;
    }

    public boolean isActive() {
        return active;
    }
}
