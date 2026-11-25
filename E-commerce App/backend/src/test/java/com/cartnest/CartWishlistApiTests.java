package com.cartnest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cartnest.entity.ProductVariant;
import com.cartnest.repository.ProductVariantRepository;
import com.jayway.jsonpath.JsonPath;

class CartWishlistApiTests extends ApiTestSupport {

    @Autowired
    private ProductVariantRepository variantRepository;

    private ProductVariant variant(String sku) {
        return variantRepository.findAll().stream().filter(v -> v.getSku().equals(sku)).findFirst().orElseThrow();
    }

    private String addBody(long variantId, int quantity) {
        return "{\"variantId\":" + variantId + ",\"quantity\":" + quantity + "}";
    }

    @Test
    void addingTheSameVariantTwiceRaisesTheQuantityAndTotalsAddUp() throws Exception {
        String token = newCustomer();
        ProductVariant phone = variant("SMA-APP-IPH-123-128GB");     // iPhone 13 Pro, 128 GB

        mvc.perform(as(token, post("/api/cart/items")).content(addBody(phone.getId(), 1))).andExpect(status().isOk());
        mvc.perform(as(token, post("/api/cart/items")).content(addBody(phone.getId(), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].variantLabel").value("128 GB"))
                .andExpect(jsonPath("$.summary.itemCount").value(2))
                .andExpect(jsonPath("$.summary.subtotal").value(phone.getPrice().doubleValue() * 2))
                .andExpect(jsonPath("$.summary.totalMrp").value(phone.getMrp().doubleValue() * 2))
                .andExpect(jsonPath("$.summary.deliveryFee").value(0))
                .andExpect(jsonPath("$.summary.total").value(phone.getPrice().doubleValue() * 2))
                .andExpect(jsonPath("$.checkoutReady").value(true));
    }

    @Test
    void smallOrdersPayDelivery() throws Exception {
        String token = newCustomer();
        ProductVariant lemon = variant("GRO-BRD-LEM-031-STANDARD");
        mvc.perform(as(token, post("/api/cart/items")).content(addBody(lemon.getId(), 1)))
                .andExpect(jsonPath("$.summary.deliveryFee").value(40))
                .andExpect(jsonPath("$.summary.total").value(lemon.getPrice().doubleValue() + 40));
    }

    @Test
    void quantityRulesAreEnforced() throws Exception {
        String token = newCustomer();
        ProductVariant phone = variant("SMA-APP-IPH-123-128GB");
        mvc.perform(as(token, post("/api/cart/items")).content(addBody(phone.getId(), 11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("quantity must be between 1 and 10"));

        ProductVariant soldOut = variantRepository.findAll().stream().filter(v -> v.getStock() == 0).findFirst()
                .orElseThrow();
        mvc.perform(as(token, post("/api/cart/items")).content(addBody(soldOut.getId(), 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("is out of stock")));

        ProductVariant scarce = variantRepository.findAll().stream().filter(v -> v.getStock() > 0 && v.getStock() < 5)
                .findFirst().orElseThrow();
        mvc.perform(as(token, post("/api/cart/items")).content(addBody(scarce.getId(), scarce.getStock() + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Only " + scarce.getStock())));

        mvc.perform(as(token, post("/api/cart/items")).content(addBody(999999, 1))).andExpect(status().isNotFound());
    }

    @Test
    void linesCanBeChangedRemovedAndCleared() throws Exception {
        String token = newCustomer();
        String json = mvc.perform(as(token, post("/api/cart/items"))
                        .content(addBody(variant("SMA-APP-IPH-123-128GB").getId(), 1)))
                .andReturn().getResponse().getContentAsString();
        Integer line = JsonPath.read(json, "$.items[0].id");
        mvc.perform(as(token, put("/api/cart/items/" + line)).content("{\"quantity\":3}"))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        mvc.perform(as(token, delete("/api/cart/items/" + line))).andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(as(token, post("/api/cart/items")).content(addBody(variant("GRO-BRD-LEM-031-STANDARD").getId(), 2)));
        mvc.perform(as(token, delete("/api/cart")))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.checkoutReady").value(false));

        // another customer's line looks like it does not exist
        mvc.perform(as(newCustomer(), delete("/api/cart/items/" + line))).andExpect(status().isNotFound());
    }

    @Test
    void aLineWithTooLittleStockLeftBlocksCheckout() throws Exception {
        String token = newCustomer();
        ProductVariant phone = variant("SMA-OPP-OPP-125-128GB");
        int originalStock = phone.getStock();
        try {
            mvc.perform(as(token, post("/api/cart/items")).content(addBody(phone.getId(), 3)));
            phone.setStock(1);
            variantRepository.save(phone);
            mvc.perform(as(token, get("/api/cart")))
                    .andExpect(jsonPath("$.items[0].problem").value("Only 1 left: reduce the quantity"))
                    .andExpect(jsonPath("$.summary.subtotal").value(0))
                    .andExpect(jsonPath("$.checkoutReady").value(false));
        } finally {
            phone.setStock(originalStock);
            variantRepository.save(phone);
        }
    }

    @Test
    void wishlistAddsOnceAndCanReceiveACartLine() throws Exception {
        String token = newCustomer();
        ProductVariant phone = variant("SMA-APP-IPH-123-128GB");
        Long productId = variantRepository.findWithProductById(phone.getId()).orElseThrow().getProduct().getId();

        mvc.perform(as(token, post("/api/wishlist")).content("{\"productId\":" + productId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].product.slug").value("iphone-13-pro"));
        mvc.perform(as(token, post("/api/wishlist")).content("{\"productId\":" + productId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(as(token, delete("/api/wishlist/" + productId))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(token, post("/api/wishlist")).content("{\"productId\":999999}")).andExpect(status().isNotFound());

        // "Save for later" moves a cart line into the wishlist
        String cart = mvc.perform(as(token, post("/api/cart/items")).content(addBody(phone.getId(), 1)))
                .andReturn().getResponse().getContentAsString();
        Integer line = JsonPath.read(cart, "$.items[0].id");
        mvc.perform(as(token, post("/api/cart/items/" + line + "/move-to-wishlist")))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(as(token, get("/api/wishlist"))).andExpect(jsonPath("$[0].product.slug").value("iphone-13-pro"));
    }
}
