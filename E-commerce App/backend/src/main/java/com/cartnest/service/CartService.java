package com.cartnest.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.CartAddRequest;
import com.cartnest.dto.CartItemResponse;
import com.cartnest.dto.CartResponse;
import com.cartnest.dto.CartSummary;
import com.cartnest.entity.CartItem;
import com.cartnest.entity.Product;
import com.cartnest.entity.ProductVariant;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.CartItemRepository;
import com.cartnest.repository.ProductVariantRepository;
import com.cartnest.repository.UserRepository;

/**
 * The customer's cart, stored on the server so it follows them between devices.
 * Rules: at most 10 of one item, never more than the stock, at most 50 different items.
 * Stock is only checked here; it is reserved when the order is placed.
 */
@Service
@Transactional
public class CartService {

    public static final int MAX_QUANTITY_PER_ITEM = 10;
    public static final int MAX_LINES = 50;
    public static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("499");
    public static final BigDecimal DELIVERY_FEE = new BigDecimal("40");

    private final CartItemRepository cartRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final WishlistService wishlistService;

    public CartService(CartItemRepository cartRepository, ProductVariantRepository variantRepository,
                       UserRepository userRepository, WishlistService wishlistService) {
        this.cartRepository = cartRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
        this.wishlistService = wishlistService;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return toResponse(cartRepository.findByUserIdOrderByAddedAtDescIdDesc(userId));
    }

    /** Adds to the quantity already in the cart, or creates the line. */
    public CartResponse add(Long userId, CartAddRequest request) {
        ProductVariant variant = variantRepository.findWithProductById(request.variantId())
                .filter(v -> v.getProduct().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Product option " + request.variantId() + " not found"));
        int adding = request.quantity() == null ? 1 : request.quantity();

        CartItem line = cartRepository.findByUserIdAndVariantId(userId, variant.getId()).orElse(null);
        if (line == null && cartRepository.countByUserId(userId) >= MAX_LINES) {
            throw new ConflictException("Your cart can hold at most " + MAX_LINES + " different items");
        }
        int newQuantity = (line == null ? 0 : line.getQuantity()) + adding;
        checkQuantity(variant, newQuantity);

        if (line == null) {
            cartRepository.save(new CartItem(userRepository.getReferenceById(userId), variant, newQuantity));
        } else {
            line.setQuantity(newQuantity);
        }
        return getCart(userId);
    }

    public CartResponse updateQuantity(Long userId, Long itemId, int quantity) {
        CartItem line = find(userId, itemId);
        checkQuantity(line.getVariant(), quantity);
        line.setQuantity(quantity);
        return getCart(userId);
    }

    public CartResponse remove(Long userId, Long itemId) {
        cartRepository.delete(find(userId, itemId));
        cartRepository.flush();
        return getCart(userId);
    }

    public CartResponse clear(Long userId) {
        cartRepository.deleteByUserId(userId);
        cartRepository.flush();
        return getCart(userId);
    }

    /** "Save for later": the product goes to the wishlist and the line leaves the cart. */
    public CartResponse moveToWishlist(Long userId, Long itemId) {
        CartItem line = find(userId, itemId);
        wishlistService.add(userId, line.getVariant().getProduct().getId());
        cartRepository.delete(line);
        cartRepository.flush();
        return getCart(userId);
    }

    private CartItem find(Long userId, Long itemId) {
        return cartRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item " + itemId + " not found"));
    }

    private static void checkQuantity(ProductVariant variant, int quantity) {
        String name = displayName(variant);
        if (variant.getStock() == 0) {
            throw new ConflictException(name + " is out of stock");
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new ConflictException("You can buy at most " + MAX_QUANTITY_PER_ITEM + " of " + name);
        }
        if (quantity > variant.getStock()) {
            throw new ConflictException("Only " + variant.getStock() + " of " + name + " left in stock");
        }
    }

    private static String displayName(ProductVariant variant) {
        Product product = variant.getProduct();
        return switch (product.getVariantType()) {
            case NONE -> product.getTitle();
            default -> product.getTitle() + " (" + variant.getLabel() + ")";
        };
    }

    /**
     * Lines that cannot be bought as they are (product withdrawn, sold out, fewer left than wanted) are listed with
     * a problem and left out of the totals; checkout stays blocked until they are fixed.
     */
    static CartResponse toResponse(List<CartItem> lines) {
        List<CartItemResponse> items = lines.stream().map(CartService::toItem).toList();
        List<CartItemResponse> buyable = items.stream().filter(i -> i.problem() == null).toList();

        BigDecimal totalMrp = buyable.stream()
                .map(i -> i.mrp().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = buyable.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivery = subtotal.signum() == 0 || subtotal.compareTo(FREE_DELIVERY_THRESHOLD) >= 0
                ? BigDecimal.ZERO : DELIVERY_FEE;
        int count = buyable.stream().mapToInt(CartItemResponse::quantity).sum();

        CartSummary summary = new CartSummary(count, totalMrp, totalMrp.subtract(subtotal), subtotal, delivery,
                subtotal.add(delivery), FREE_DELIVERY_THRESHOLD);
        boolean ready = !items.isEmpty() && buyable.size() == items.size();
        return new CartResponse(items, summary, ready);
    }

    private static CartItemResponse toItem(CartItem line) {
        ProductVariant v = line.getVariant();
        Product p = v.getProduct();
        String problem = null;
        if (!p.isActive()) {
            problem = "No longer available";
        } else if (v.getStock() == 0) {
            problem = "Out of stock";
        } else if (line.getQuantity() > v.getStock()) {
            problem = "Only " + v.getStock() + " left: reduce the quantity";
        }
        return new CartItemResponse(line.getId(), p.getId(), p.getSlug(), p.getTitle(),
                p.getBrand() == null ? null : p.getBrand().getName(), p.getThumbnailUrl(), v.getId(), v.getLabel(),
                p.getVariantType(), v.getPrice(), v.getMrp(), line.getQuantity(),
                Math.min(MAX_QUANTITY_PER_ITEM, v.getStock()),
                v.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())), problem);
    }
}
