package com.springlab.repository.projection;

/** One group of a progress breakdown (topic or difficulty): questions presented and how many were answered correctly. */
public record BreakdownRow(String key, String label, long questions, long correct) {
}
