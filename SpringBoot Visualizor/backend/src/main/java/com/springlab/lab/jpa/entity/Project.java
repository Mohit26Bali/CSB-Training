package com.springlab.lab.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Cascade ALL but NO orphanRemoval: shows that the two are different concepts. */
@Entity
@Table(name = "jpa_lab_project")
public class Project extends LabBase {

    @Column(nullable = false, length = 80)
    private String name;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<Task> tasks = new ArrayList<>();

    protected Project() {
    }

    public Project(String runKey, String name) {
        super(runKey);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void addTask(Task task) {
        tasks.add(task);
        task.setProject(this);
    }
}
