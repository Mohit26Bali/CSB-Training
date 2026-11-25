package com.cartnest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.admin.DailySales;
import com.cartnest.dto.admin.DashboardResponse;
import com.cartnest.dto.admin.LowStockItem;
import com.cartnest.dto.admin.TopProduct;
import com.cartnest.entity.CustomerOrder;
import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.Product;
import com.cartnest.entity.Role;
import com.cartnest.repository.OrderItemRepository;
import com.cartnest.repository.OrderRepository;
import com.cartnest.repository.ProductRepository;
import com.cartnest.repository.ProductSales;
import com.cartnest.repository.ProductVariantRepository;
import com.cartnest.repository.StatusCount;
import com.cartnest.repository.UserRepository;

/** The admin home page: sales figures, orders by status, a 14-day sales chart, best sellers and low stock. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    public static final int LOW_STOCK_THRESHOLD = 5;
    private static final int CHART_DAYS = 14;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    public DashboardService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                            ProductRepository productRepository, ProductVariantRepository variantRepository,
                            UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
    }

    public DashboardResponse getDashboard() {
        BigDecimal revenue = orderRepository.totalRevenue();
        long orders = orderRepository.countNotCancelled();
        BigDecimal average = orders == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);

        // Every status is listed, in pipeline order, even when its count is 0
        Map<OrderStatus, Long> counts = orderRepository.countByStatus().stream()
                .collect(Collectors.toMap(StatusCount::status, StatusCount::total));
        Map<OrderStatus, Long> byStatus = AdminOrderService.pipeline().stream()
                .collect(Collectors.toMap(s -> s, s -> counts.getOrDefault(s, 0L), (a, b) -> a, LinkedHashMap::new));

        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(CHART_DAYS - 1);
        Map<LocalDate, List<CustomerOrder>> perDay = orderRepository.findNotCancelledSince(firstDay.atStartOfDay())
                .stream().collect(Collectors.groupingBy(o -> o.getPlacedAt().toLocalDate()));
        List<DailySales> chart = firstDay.datesUntil(today.plusDays(1))
                .map(day -> {
                    List<CustomerOrder> dayOrders = perDay.getOrDefault(day, List.of());
                    return new DailySales(day, dayOrders.size(), dayOrders.stream().map(CustomerOrder::getGrandTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                })
                .toList();
        DailySales todaySales = chart.get(chart.size() - 1);

        return new DashboardResponse(revenue, orders, average, todaySales.orders(), todaySales.revenue(),
                userRepository.countByRole(Role.CUSTOMER), productRepository.countByActiveTrue(), byStatus, chart,
                topProducts(), lowStock(),
                orderRepository.findTop8ByOrderByPlacedAtDescIdDesc().stream().map(AdminOrderService::toRow).toList());
    }

    private List<TopProduct> topProducts() {
        List<ProductSales> sales = orderItemRepository.bestSellers(PageRequest.of(0, 5));
        Map<Long, Product> products = productRepository.findAllById(sales.stream().map(ProductSales::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return sales.stream()
                .map(s -> {
                    Product p = products.get(s.productId());
                    return new TopProduct(p.getId(), p.getSlug(), p.getTitle(), p.getThumbnailUrl(), s.units(), s.revenue());
                })
                .toList();
    }

    private List<LowStockItem> lowStock() {
        return variantRepository.findLowStock(LOW_STOCK_THRESHOLD, PageRequest.of(0, 10)).stream()
                .map(v -> new LowStockItem(v.getId(), v.getProduct().getId(), v.getProduct().getSlug(),
                        v.getProduct().getTitle(), v.getLabel(), v.getSku(), v.getStock()))
                .toList();
    }
}
