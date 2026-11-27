package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Owning side of Project 1 --- * Task. The fetch type is NOT written on purpose: the Jakarta Persistence default for
 * @ManyToOne is EAGER, which the fetching experiments demonstrate (contrast with Employee, which says LAZY).
 */
@Entity
@Table(name = "jpa_lab_task")
public class Task extends LabBase {

    @Column(nullable = false, length = 80)
    private String title;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    protected Task() {
    }

    public Task(String runKey, String title) {
        super(runKey);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }
}
