package com.hackathon.enrollment.exception;

public class CourseInactiveException extends RuntimeException {

    public CourseInactiveException(String message) {
        super(message);
    }
}
