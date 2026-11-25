package com.cartnest.repository;

/** A cart line reduced to what checkout needs: which variant and how many. */
public record CartLine(Long variantId, int quantity) {
}
