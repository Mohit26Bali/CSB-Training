package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** The OWNING side of Account 1 --- 1 Profile: account_id is a foreign key with a UNIQUE constraint. */
@Entity
@Table(name = "jpa_lab_profile")
public class Profile extends LabBase {

    @Column(nullable = false, length = 200)
    private String bio;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    protected Profile() {
    }

    public Profile(String runKey, String bio) {
        super(runKey);
        this.bio = bio;
    }

    public String getBio() {
        return bio;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }
}
