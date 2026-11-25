package com.cartnest.dto;

import java.util.List;

public record CategoryDetail(Long id, String name, String slug, String imageUrl, long productCount,
        List<CategoryRef> breadcrumb, List<CategoryNode> children) {
}
