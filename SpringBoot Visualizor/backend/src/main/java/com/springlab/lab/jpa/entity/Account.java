package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** The INVERSE side of Account 1 --- 1 Profile (mappedBy): the foreign key is in the profile table. */
@Entity
@Table(name = "jpa_lab_account")
public class Account extends LabBase {

    @Column(nullable = false, length = 60)
    private String username;

    @OneToOne(mappedBy = "account")
    private Profile profile;

    protected Account() {
    }

    public Account(String runKey, String username) {
        super(runKey);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public Profile getProfile() {
        return profile;
    }

    /** Sets both sides, as a bidirectional association requires. */
    public void attachProfile(Profile newProfile) {
        this.profile = newProfile;
        newProfile.setAccount(this);
    }
}
