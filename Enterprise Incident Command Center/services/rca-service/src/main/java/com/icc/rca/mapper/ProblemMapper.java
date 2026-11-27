package com.icc.rca.mapper;

import com.icc.rca.dto.ActionResponse;
import com.icc.rca.dto.ProblemResponse;
import com.icc.rca.entity.CorrectiveAction;
import com.icc.rca.entity.Problem;
import com.icc.rca.entity.ProblemAction;
import org.springframework.stereotype.Component;

@Component
public class ProblemMapper {

    public ProblemResponse toResponse(Problem p) {
        return new ProblemResponse(p.getId(), p.getNumber(), p.getTitle(), p.getDescription(), p.getStatus(),
                ProblemAction.availableIn(p.getStatus()),
                p.getRootCause(), p.getRootCauseCategory(), p.getWorkaround(), p.getFixNotes(),
                p.getResolutionCode(), p.getDuplicateOfId(), p.isWorkaroundApplied(), p.getAssignedToId(),
                p.getImpact(), p.getUrgency(), p.getPriority(), p.getMajorIncidentId(), p.getReopenCount(),
                p.getCreatedById(), p.getCreatedAt(), p.getUpdatedAt(), p.getResolvedAt(), p.getClosedAt(),
                p.getSyncStatus());
    }

    public ActionResponse toAction(CorrectiveAction a) {
        return new ActionResponse(a.getId(), a.getProblemId(), a.getDescription(), a.getOwnerId(),
                a.getDueDate(), a.getStatus());
    }
}
