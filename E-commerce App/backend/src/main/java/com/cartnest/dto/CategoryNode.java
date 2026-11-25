package com.cartnest.dto;

import java.util.List;

/** A category with its product count (including sub-categories) and its children. */
public record CategoryNode(Long id, String name, String slug, String imageUrl, long productCount, List<CategoryNode> children) {
}
