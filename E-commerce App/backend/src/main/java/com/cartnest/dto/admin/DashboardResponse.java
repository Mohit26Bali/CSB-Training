package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.cartnest.entity.OrderStatus;

/** Revenue and order counts leave out cancelled orders. */
public record DashboardResponse(BigDecimal revenue, long orders, BigDecimal averageOrderValue, long ordersToday, BigDecimal revenueToday,
        long customers, long activeProducts, Map<OrderStatus, Long> ordersByStatus, List<DailySales> salesLast14Days,
        List<TopProduct> topProducts, List<LowStockItem> lowStock, List<AdminOrderRow> recentOrders) {
}
