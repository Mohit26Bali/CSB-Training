package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The INVERSE side of Department 1 --- * Employee: it has no foreign key column. The collections below are mapped by the
 * owning side field (mappedBy), so changing them alone never changes a foreign key. No cascade is declared.
 */
@Entity
@Table(name = "jpa_lab_department")
public class Department extends LabBase {

    @Column(nullable = false, length = 80)
    private String name;

    @OneToMany(mappedBy = "department")
    private List<Employee> employees = new ArrayList<>();

    @OneToMany(mappedBy = "department")
    private Set<Room> rooms = new LinkedHashSet<>();

    protected Department() {
    }

    public Department(String runKey, String name) {
        super(runKey);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public Set<Room> getRooms() {
        return rooms;
    }

    /** Keeps BOTH sides of the association consistent: the inverse collection and the owning reference. */
    public void addEmployee(Employee employee) {
        employees.add(employee);
        employee.setDepartment(this);
    }

    public void removeEmployee(Employee employee) {
        employees.remove(employee);
        employee.setDepartment(null);
    }

    public void addRoom(Room room) {
        rooms.add(room);
        room.setDepartment(this);
    }
}
