package com.cartnest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.WishlistItemResponse;
import com.cartnest.entity.Product;
import com.cartnest.entity.WishlistItem;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.ProductRepository;
import com.cartnest.repository.UserRepository;
import com.cartnest.repository.WishlistItemRepository;

@Service
@Transactional
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductMapper mapper;

    public WishlistService(WishlistItemRepository wishlistRepository, ProductRepository productRepository,
                           UserRepository userRepository, ProductMapper mapper) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    /** Newest first. Products that were later deactivated stay listed (shown as unavailable by inStock=false). */
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> list(Long userId) {
        return wishlistRepository.findByUserIdOrderByAddedAtDescIdDesc(userId).stream()
                .map(item -> new WishlistItemResponse(mapper.toCard(item.getProduct()), item.getAddedAt()))
                .toList();
    }

    /** Returns true when the product was added, false when it was already in the wishlist. */
    public boolean add(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
        if (wishlistRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return false;
        }
        wishlistRepository.save(new WishlistItem(userRepository.getReferenceById(userId), product));
        return true;
    }

    /** Removing something that is not in the wishlist is not an error: the result is the same. */
    public void remove(Long userId, Long productId) {
        wishlistRepository.findByUserIdAndProductId(userId, productId).ifPresent(wishlistRepository::delete);
    }
}
