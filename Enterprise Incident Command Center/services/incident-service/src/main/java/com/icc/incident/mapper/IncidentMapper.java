package com.icc.incident.mapper;

import com.icc.incident.dto.CommentResponse;
import com.icc.incident.dto.IncidentResponse;
import com.icc.incident.dto.InternalIncidentResponse;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import org.springframework.stereotype.Component;

@Component
public class IncidentMapper {

    public IncidentResponse toResponse(Incident i) {
        return new IncidentResponse(i.getId(), i.getNumber(), i.getShortDescription(), i.getDescription(),
                i.getCategory(), i.getSubcategory(), i.getImpact(), i.getUrgency(), i.getPriority(), i.getState(),
                i.getReporterId(), i.getAssignedTeamId(), i.getAssignedToId(),
                i.getHoldReason(), i.getResolutionCode(), i.getResolutionNotes(), i.getMajorIncidentId(),
                i.getCreatedAt(), i.getUpdatedAt(), i.getResolvedAt(), i.getClosedAt(), i.getSyncStatus());
    }

    public CommentResponse toComment(IncidentComment c) {
        return new CommentResponse(c.getId(), c.getIncidentId(), c.getType(), c.getBody(),
                c.getAuthorId(), c.getCreatedAt());
    }

    public InternalIncidentResponse toInternal(Incident i) {
        return new InternalIncidentResponse(i.getId(), i.getNumber(), i.getShortDescription(),
                i.getPriority(), i.getState().name(), i.getMajorIncidentId());
    }
}
