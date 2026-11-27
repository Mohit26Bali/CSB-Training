package com.hackathon.enrollment.exception;

public class CourseFullException extends RuntimeException {

    public CourseFullException(String message) {
        super(message);
    }
}
