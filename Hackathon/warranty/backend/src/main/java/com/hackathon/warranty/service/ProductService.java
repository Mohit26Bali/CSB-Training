package com.hackathon.warranty.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.warranty.dto.ProductRequest;
import com.hackathon.warranty.dto.ProductResponse;
import com.hackathon.warranty.entity.ClaimStatus;
import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.ProductCategory;
import com.hackathon.warranty.entity.User;
import com.hackathon.warranty.exception.FieldValidationException;
import com.hackathon.warranty.exception.ResourceNotFoundException;
import com.hackathon.warranty.repo.ClaimRepository;
import com.hackathon.warranty.repo.ProductRepository;
import com.hackathon.warranty.repo.UserRepository;

@Service
public class ProductService {

    private static final int MAX_AGE_YEARS = 10;

    private final ProductRepository products;
    private final ClaimRepository claims;
    private final UserRepository users;

    public ProductService(ProductRepository products, ClaimRepository claims, UserRepository users) {
        this.products = products;
        this.claims = claims;
        this.users = users;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ProductResponse register(String username, ProductRequest request) {
        User owner = findUser(username);
        ProductCategory category = parseCategory(request.category());
        LocalDate today = LocalDate.now();
        if (request.purchaseDate().isAfter(today)) {
            throw new FieldValidationException("purchaseDate", "Purchase date cannot be in the future");
        }
        if (request.purchaseDate().isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw new FieldValidationException("purchaseDate", "Purchase date cannot be more than " + MAX_AGE_YEARS + " years ago");
        }
        String serial = request.serialNumber().trim().toUpperCase(Locale.ROOT);
        Product product = new Product(owner, request.name().trim(), request.brand().trim(), category, serial,
                request.purchaseDate(), request.warrantyMonths());
        if (products.existsBySerialNumber(serial)) {
            throw serialTaken();
        }
        try {
            return ProductResponse.from(products.saveAndFlush(product), today, false);
        } catch (DataIntegrityViolationException ex) { // someone registered the same serial at the same moment
            throw serialTaken();
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<ProductResponse> myProducts(String username, String search) {
        User owner = findUser(username);
        LocalDate today = LocalDate.now();
        Set<Long> withClaims = Set.copyOf(claims.findProductIdsWithClaims(owner, EnumSet.of(ClaimStatus.PENDING,
                ClaimStatus.APPROVED, ClaimStatus.IN_REPAIR)));
        String term = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        return products.findByOwnerOrderByPurchaseDateDesc(owner).stream()
                .filter(p -> term.isEmpty()
                        || p.getName().toLowerCase(Locale.ROOT).contains(term)
                        || p.getBrand().toLowerCase(Locale.ROOT).contains(term)
                        || p.getSerialNumber().toLowerCase(Locale.ROOT).contains(term))
                .map(p -> ProductResponse.from(p, today, withClaims.contains(p.getId())))
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> categories() {
        return Arrays.stream(ProductCategory.values())
                .map(c -> Map.of("value", c.name(), "label", c.getLabel()))
                .collect(Collectors.toList());
    }

    private FieldValidationException serialTaken() {
        return new FieldValidationException("serialNumber", "A product with this serial number is already registered");
    }

    private User findUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ProductCategory parseCategory(String value) {
        try {
            return ProductCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("category", "Unknown category '" + value + "'");
        }
    }
}
