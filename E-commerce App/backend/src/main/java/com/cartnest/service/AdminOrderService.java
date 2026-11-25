package com.cartnest.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.OrderDetailResponse;
import com.cartnest.dto.admin.AdminOrderRow;
import com.cartnest.dto.admin.PageResponse;
import com.cartnest.entity.CustomerOrder;
import com.cartnest.entity.OrderItem;
import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.PaymentMethod;
import com.cartnest.entity.PaymentStatus;
import com.cartnest.entity.User;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.OrderRepository;

import jakarta.persistence.criteria.Join;

/** Order handling for admins: find orders and move them along PLACED → CONFIRMED → SHIPPED → DELIVERED. */
@Service
public class AdminOrderService {

    /** The only forward moves allowed. Cancelling has its own endpoint (and is only possible before shipping). */
    private static final Map<OrderStatus, OrderStatus> NEXT = Map.of(
            OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.CONFIRMED, OrderStatus.SHIPPED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderMapper mapper;

    public AdminOrderService(OrderRepository orderRepository, OrderService orderService, OrderMapper mapper) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.mapper = mapper;
    }

    /** Newest first; filter by status and/or text (order number, customer name or email). */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderRow> list(OrderStatus status, String q, int page, int size) {
        AdminProductService.checkPaging(page, size);
        Specification<CustomerOrder> spec = Specification.allOf();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> {
                Join<CustomerOrder, User> user = root.join("user");
                return cb.or(cb.like(cb.lower(root.get("orderNumber")), pattern),
                        cb.like(cb.lower(user.get("email")), pattern),
                        cb.like(cb.lower(user.get("fullName")), pattern));
            });
        }
        Page<CustomerOrder> orders = orderRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("placedAt").descending().and(Sort.by("id").descending())));
        return new PageResponse<>(orders.getContent().stream().map(AdminOrderService::toRow).toList(),
                orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse get(String orderNumber) {
        return mapper.toDetail(orderRepository.findByOrderNumber(orderNumber).orElseThrow(() -> notFound(orderNumber)));
    }

    /** One step forward. Delivering a cash-on-delivery order also records the cash as paid. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDetailResponse moveTo(String orderNumber, OrderStatus target, String note, String adminEmail) {
        CustomerOrder order = orderRepository.lockByOrderNumber(orderNumber).orElseThrow(() -> notFound(orderNumber));
        if (target == OrderStatus.CANCELLED) {
            throw new ConflictException("Use the cancel action to cancel an order");
        }
        if (NEXT.get(order.getStatus()) != target) {
            throw new ConflictException("An order that is " + order.getStatus() + " cannot be moved to " + target
                    + (NEXT.containsKey(order.getStatus()) ? "; the next step is " + NEXT.get(order.getStatus()) : ""));
        }
        if (target == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD
                && order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.markPaid("COD" + PaymentGateway.randomCode(12));
        }
        order.moveTo(target, note == null || note.isBlank() ? defaultNote(target) : note.trim(), adminEmail);
        return mapper.toDetail(order);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDetailResponse cancel(String orderNumber, String reason, String adminEmail) {
        CustomerOrder order = orderRepository.lockByOrderNumber(orderNumber).orElseThrow(() -> notFound(orderNumber));
        orderService.cancel(order, reason == null || reason.isBlank() ? "Cancelled by CartNest" : reason.trim(), adminEmail);
        return mapper.toDetail(order);
    }

    private static String defaultNote(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "Order confirmed by the seller";
            case SHIPPED -> "Handed over to the courier";
            case DELIVERED -> "Delivered";
            default -> null;
        };
    }

    static AdminOrderRow toRow(CustomerOrder o) {
        return new AdminOrderRow(o.getOrderNumber(), o.getPlacedAt(), o.getStatus(), o.getPaymentMethod(),
                o.getPaymentStatus(), o.getGrandTotal(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                o.getUser().getFullName(), o.getUser().getEmail(), o.getShipCity());
    }

    private static ResourceNotFoundException notFound(String orderNumber) {
        return new ResourceNotFoundException("Order " + orderNumber + " not found");
    }

    /** Statuses an admin can filter by, in pipeline order. */
    public static List<OrderStatus> pipeline() {
        return List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                OrderStatus.CANCELLED);
    }
}
