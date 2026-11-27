package com.icc.rca.dto;

import jakarta.validation.constraints.Size;

/** Accepting the risk: a workaround is required when it was applied (as in ServiceNow). */
public record AcceptRiskRequest(
        boolean workaroundApplied,
        @Size(max = 4000) String workaround) {
}
