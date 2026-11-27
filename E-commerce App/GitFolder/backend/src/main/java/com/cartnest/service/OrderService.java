package com.cartnest.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.AddressResponse;
import com.cartnest.dto.CartResponse;
import com.cartnest.dto.CheckoutPreview;
import com.cartnest.dto.CheckoutPreviewRequest;
import com.cartnest.dto.OrderDetailResponse;
import com.cartnest.dto.OrderPage;
import com.cartnest.dto.PlaceOrderRequest;
import com.cartnest.entity.Address;
import com.cartnest.entity.CustomerOrder;
import com.cartnest.entity.OrderItem;
import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.PaymentMethod;
import com.cartnest.entity.PaymentStatus;
import com.cartnest.entity.ProductVariant;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.AddressRepository;
import com.cartnest.repository.CartItemRepository;
import com.cartnest.repository.CartLine;
import com.cartnest.repository.OrderRepository;
import com.cartnest.repository.ProductVariantRepository;
import com.cartnest.repository.UserRepository;
import com.cartnest.security.SecurityUser;

@Service
public class OrderService {
    public static final BigDecimal COD_LIMIT = new BigDecimal("50000");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final CartItemRepository cartRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CouponService couponService;
    private final PaymentGateway paymentGateway;
    private final OrderMapper mapper;

    public OrderService(OrderRepository orderRepository, CartItemRepository cartRepository,
                        AddressRepository addressRepository, ProductVariantRepository variantRepository,
                        UserRepository userRepository, CouponService couponService, PaymentGateway paymentGateway,
                        OrderMapper mapper) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.addressRepository = addressRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
        this.couponService = couponService;
        this.paymentGateway = paymentGateway;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long userId, CheckoutPreviewRequest request) {
        CartResponse cart = CartService.toResponse(cartRepository.findByUserIdOrderByAddedAtDescIdDesc(userId));
        BigDecimal itemTotal = cart.summary().subtotal();

        String couponCode = null;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponMessage = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            try {
                CouponService.Quote quote = couponService.preview(request.couponCode(), userId, itemTotal);
                couponCode = quote.coupon().getCode();
                couponDiscount = quote.discount();
                couponMessage = "You save " + CouponService.rupees(couponDiscount) + " with " + couponCode;
            } catch (BadRequestException ex) {
                couponMessage = ex.getMessage();
            }
        }

        AddressResponse address = (request.addressId() == null
                ? addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(userId).stream().findFirst()
                : addressRepository.findByIdAndUserId(request.addressId(), userId))
                .map(AddressService::toResponse).orElse(null);

        BigDecimal delivery = deliveryFee(itemTotal);
        BigDecimal grandTotal = itemTotal.subtract(couponDiscount).add(delivery);
        return new CheckoutPreview(cart.items(), cart.summary().itemCount(), cart.summary().totalMrp(),
                cart.summary().discount(), itemTotal, couponCode, couponDiscount, couponMessage, delivery, grandTotal,
                grandTotal.compareTo(COD_LIMIT) <= 0, address, cart.checkoutReady() && address != null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDetailResponse placeOrder(SecurityUser customer, PlaceOrderRequest request) {
        Long userId = customer.getId();

        List<CartLine> lines = cartRepository.findLines(userId);
        if (lines.isEmpty()) {
            throw new ConflictException("Your cart is empty");
        }
        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address " + request.addressId() + " not found"));

        Set<Long> variantIds = lines.stream().map(CartLine::variantId).collect(Collectors.toSet());
        Map<Long, ProductVariant> locked = variantRepository.lockAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<String> problems = lines.stream()
                .map(line -> stockProblem(locked.get(line.variantId()), line.quantity()))
                .filter(p -> p != null)
                .toList();
        if (!problems.isEmpty()) {
            throw new ConflictException(String.join("; ", problems));
        }

        CustomerOrder order = new CustomerOrder(newOrderNumber(), userRepository.getReferenceById(userId),
                request.paymentMethod(), address);
        lines.forEach(line -> order.addItem(locked.get(line.variantId()), line.quantity()));

        BigDecimal totalMrp = order.getItems().stream()
                .map(i -> i.getUnitMrp().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemTotal = order.getItems().stream().map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CouponService.Quote coupon = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            coupon = couponService.lockAndQuote(request.couponCode(), userId, itemTotal);
        }
        order.setTotals(totalMrp, itemTotal, coupon == null ? null : coupon.coupon().getCode(),
                coupon == null ? BigDecimal.ZERO : coupon.discount(), deliveryFee(itemTotal));

        if (request.paymentMethod() == PaymentMethod.COD && order.getGrandTotal().compareTo(COD_LIMIT) > 0) {
            throw new BadRequestException("Cash on delivery is available for orders up to "
                    + CouponService.rupees(COD_LIMIT) + ". Please pay by UPI or card");
        }

        lines.forEach(line -> {
            ProductVariant v = locked.get(line.variantId());
            v.setStock(v.getStock() - line.quantity());
        });

        if (request.paymentMethod() != PaymentMethod.COD) {
            order.markPaid(paymentGateway.charge(request.paymentMethod(), order.getGrandTotal(), request.upiId()));
        }
        order.moveTo(OrderStatus.PLACED, "Order placed", customer.getEmail());
        orderRepository.save(order);
        if (coupon != null) {
            couponService.redeem(coupon.coupon(), userId, order);
        }
        cartRepository.deleteByUserId(userId);
        return mapper.toDetail(order);
    }

    @Transactional(readOnly = true)
    public OrderPage myOrders(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BadRequestException("page must be 0 or more and size between 1 and 50");
        }
        Page<CustomerOrder> orders = orderRepository.findByUserId(userId,
                PageRequest.of(page, size, Sort.by("placedAt").descending().and(Sort.by("id").descending())));
        return new OrderPage(orders.getContent().stream().map(mapper::toSummary).toList(), orders.getNumber(),
                orders.getSize(), orders.getTotalElements(), orders.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse myOrder(Long userId, String orderNumber) {
        return mapper.toDetail(orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> notFound(orderNumber)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDetailResponse cancelMyOrder(SecurityUser customer, String orderNumber, String reason) {
        CustomerOrder order = orderRepository.lockByOrderNumber(orderNumber)
                .filter(o -> o.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> notFound(orderNumber));
        cancel(order, reason == null || reason.isBlank() ? "Cancelled by customer" : reason.trim(),
                customer.getEmail());
        return mapper.toDetail(order);
    }

    public void cancel(CustomerOrder order, String reason, String cancelledBy) {
        if (!order.isCancellable()) {
            throw new ConflictException(switch (order.getStatus()) {
                case CANCELLED -> "This order is already cancelled";
                case DELIVERED -> "This order has been delivered and can no longer be cancelled";
                default -> "This order has already shipped and can no longer be cancelled";
            });
        }
        Map<Long, ProductVariant> locked = variantRepository.lockAllById(order.getItems().stream()
                        .map(i -> i.getVariant().getId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        order.getItems().forEach(i -> {
            ProductVariant v = locked.get(i.getVariant().getId());
            v.setStock(v.getStock() + i.getQuantity());
        });

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            paymentGateway.refund(order.getPaymentReference(), order.getGrandTotal());
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        couponService.release(order);
        order.setCancelReason(reason);
        order.moveTo(OrderStatus.CANCELLED, reason, cancelledBy);
    }

    public static BigDecimal deliveryFee(BigDecimal itemTotal) {
        return itemTotal.signum() == 0 || itemTotal.compareTo(CartService.FREE_DELIVERY_THRESHOLD) >= 0
                ? BigDecimal.ZERO : CartService.DELIVERY_FEE;
    }

    private static String stockProblem(ProductVariant v, int quantity) {
        String name = v.getProduct().getTitle() + (v.getLabel().equals("Standard") ? "" : " (" + v.getLabel() + ")");
        if (!v.getProduct().isActive()) {
            return name + " is no longer available";
        }
        if (v.getStock() == 0) {
            return name + " is out of stock";
        }
        if (v.getStock() < quantity) {
            return "Only " + v.getStock() + " of " + name + " left";
        }
        return null;
    }

    private String newOrderNumber() {
        String number;
        do {
            number = "CN" + LocalDate.now().format(NUMBER_DATE)
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        } while (orderRepository.existsByOrderNumber(number));
        return number;
    }

    private static ResourceNotFoundException notFound(String orderNumber) {
        return new ResourceNotFoundException("Order " + orderNumber + " not found");
    }
}
