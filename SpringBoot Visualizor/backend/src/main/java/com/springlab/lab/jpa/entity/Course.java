package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/** The INVERSE side of Student * --- * Course (mappedBy): changing this collection alone never writes the join table. */
@Entity
@Table(name = "jpa_lab_course")
public class Course extends LabBase {

    @Column(nullable = false, length = 80)
    private String title;

    @ManyToMany(mappedBy = "courses")
    private Set<Student> students = new LinkedHashSet<>();

    protected Course() {
    }

    public Course(String runKey, String title) {
        super(runKey);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public Set<Student> getStudents() {
        return students;
    }
}
