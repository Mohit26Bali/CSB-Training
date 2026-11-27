package com.hackathon.complaints.dto;

import java.util.List;

public record ComplaintDetail(ComplaintResponse complaint, List<EventResponse> timeline) {
}
