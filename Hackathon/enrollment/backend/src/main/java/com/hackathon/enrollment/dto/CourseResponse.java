package com.hackathon.enrollment.dto;

public class CourseResponse {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String imageUrl;
    private int capacity;
    private boolean active;
    private long approvedCount;
    private long pendingCount;

    public CourseResponse(Long id, String title, String description, String category,
                          String imageUrl, int capacity, boolean active, long approvedCount, long pendingCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.imageUrl = imageUrl;
        this.capacity = capacity;
        this.active = active;
        this.approvedCount = approvedCount;
        this.pendingCount = pendingCount;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }

    public long getApprovedCount() {
        return approvedCount;
    }

    public long getPendingCount() {
        return pendingCount;
    }
}
