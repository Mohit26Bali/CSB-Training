package com.cartnest.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.BrandResponse;
import com.cartnest.repository.BrandRepository;
import com.cartnest.repository.IdCount;
import com.cartnest.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class BrandService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandService(BrandRepository brandRepository, ProductRepository productRepository) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    public List<BrandResponse> getBrands() {
        Map<Long, Long> counts = productRepository.countActiveByBrand().stream()
                .collect(Collectors.toMap(IdCount::id, IdCount::total));
        return brandRepository.findAll().stream()
                .filter(b -> counts.containsKey(b.getId()))
                .map(b -> new BrandResponse(b.getId(), b.getName(), b.getSlug(), counts.get(b.getId())))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .collect(Collectors.toList());
    }
}
