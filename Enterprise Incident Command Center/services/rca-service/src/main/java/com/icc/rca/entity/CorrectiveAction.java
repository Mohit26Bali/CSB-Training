package com.icc.rca.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "corrective_action")
public class CorrectiveAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long problemId;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Long ownerId;
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private ActionStatus status;

    protected CorrectiveAction() {
    }

    public CorrectiveAction(Long problemId, String description, Long ownerId, LocalDate dueDate) {
        this.problemId = problemId;
        this.description = description;
        this.ownerId = ownerId;
        this.dueDate = dueDate;
        this.status = ActionStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public Long getProblemId() {
        return problemId;
    }

    public String getDescription() {
        return description;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public void setStatus(ActionStatus status) {
        this.status = status;
    }
}
