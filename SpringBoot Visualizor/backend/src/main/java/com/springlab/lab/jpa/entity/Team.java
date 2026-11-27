package com.springlab.lab.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Cascade ALL plus orphanRemoval: a member removed from the collection is deleted from the database. */
@Entity
@Table(name = "jpa_lab_team")
public class Team extends LabBase {

    @Column(nullable = false, length = 80)
    private String name;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Member> members = new ArrayList<>();

    protected Team() {
    }

    public Team(String runKey, String name) {
        super(runKey);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void addMember(Member member) {
        members.add(member);
        member.setTeam(this);
    }

    public void removeMember(Member member) {
        members.remove(member);
        member.setTeam(null);
    }
}
