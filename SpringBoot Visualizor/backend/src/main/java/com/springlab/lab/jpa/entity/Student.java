package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/** The OWNING side of Student * --- * Course: it declares the join table. No cascade (and certainly no REMOVE). */
@Entity
@Table(name = "jpa_lab_student")
public class Student extends LabBase {

    @Column(nullable = false, length = 80)
    private String name;

    @ManyToMany
    @JoinTable(name = "jpa_lab_enrollment",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    private Set<Course> courses = new LinkedHashSet<>();

    protected Student() {
    }

    public Student(String runKey, String name) {
        super(runKey);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<Course> getCourses() {
        return courses;
    }

    public void enroll(Course course) {
        courses.add(course);
        course.getStudents().add(this);
    }

    public void withdraw(Course course) {
        courses.remove(course);
        course.getStudents().remove(this);
    }
}
