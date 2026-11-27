package com.hackathon.warranty.entity;

public enum ClaimPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int rank;

    ClaimPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
