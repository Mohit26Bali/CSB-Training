package com.hackathon.food.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesDashboard(long ordersToday, BigDecimal revenueToday, long openOrders, BigDecimal averageOrder, long deliveredThisWeek,
                             BigDecimal revenueThisWeek, List<StatusCount> byStatus, List<ItemSales> bestSellers, List<DayRevenue> lastSevenDays) {
}
