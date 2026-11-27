package com.icc.incident.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignRequest(@NotNull Long teamId) {
}
