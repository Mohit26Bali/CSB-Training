package com.hackathon.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssetDashboard(long totalAssets, long available, long assigned, long returnRequests, long inRepair, long retired, long overdue,
                             long poorCondition, BigDecimal assetValue, List<Count> byCategory, List<Count> byStatus, List<AssignmentResponse> oldestRequests) {
}
