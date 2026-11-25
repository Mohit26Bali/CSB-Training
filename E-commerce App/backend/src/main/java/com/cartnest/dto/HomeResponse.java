package com.cartnest.dto;

import java.util.List;

public record HomeResponse(List<CategoryNode> categories, List<HomeSection> sections) {
}
