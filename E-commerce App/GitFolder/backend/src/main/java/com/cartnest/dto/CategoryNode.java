package com.cartnest.dto;

import java.util.List;

public record CategoryNode(Long id, String name, String slug, String imageUrl, long productCount, List<CategoryNode> children) {
}
