package com.icc.majorincident.mapper;

import com.icc.majorincident.dto.InternalMajorIncidentResponse;
import com.icc.majorincident.dto.MajorIncidentResponse;
import com.icc.majorincident.dto.TimelineEntryResponse;
import com.icc.majorincident.entity.MajorIncident;
import com.icc.majorincident.entity.TimelineEntry;
import org.springframework.stereotype.Component;

@Component
public class MajorIncidentMapper {

    public MajorIncidentResponse toResponse(MajorIncident m) {
        return new MajorIncidentResponse(m.getId(), m.getIncidentId(), m.getSummary(), m.getImpactSummary(),
                m.getCommanderId(), m.getStatus(), m.getDeclaredById(), m.getDeclaredAt(),
                m.getResolvedAt(), m.getClosedAt(), m.getSyncStatus());
    }

    public TimelineEntryResponse toEntry(TimelineEntry e) {
        return new TimelineEntryResponse(e.getId(), e.getEntryType(), e.getText(), e.getAuthorId(), e.getCreatedAt());
    }

    public InternalMajorIncidentResponse toInternal(MajorIncident m) {
        return new InternalMajorIncidentResponse(m.getId(), m.getIncidentId(), m.getSummary(), m.getStatus().name());
    }
}
