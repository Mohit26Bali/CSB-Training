package com.hackathon.procurement.dto;

import jakarta.validation.constraints.Size;

/** Optional purchase-order reference recorded when a request is marked ORDERED. */
public record OrderRequest(@Size(max = 40, message = "Order reference must be at most 40 characters") String orderReference) {
}
