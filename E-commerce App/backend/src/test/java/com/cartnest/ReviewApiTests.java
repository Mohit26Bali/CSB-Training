package com.cartnest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

class ReviewApiTests extends ApiTestSupport {

    private static final String PRODUCT = "glass";

    /** A new customer who has ordered the product; when delivered is true the admin has also delivered it. */
    private String customerWithOrder(boolean delivered) throws Exception {
        String token = newCustomer();
        String product = mvc.perform(get("/api/products/" + PRODUCT)).andReturn().getResponse().getContentAsString();
        mvc.perform(as(token, post("/api/cart/items")).content("{\"variantId\":" + JsonPath.read(product, "$.variants[0].id") + "}"));
        String address = mvc.perform(as(token, post("/api/addresses")).content("{\"fullName\":\"A B\",\"phone\":\"9876543210\","
                + "\"line1\":\"1 Road\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\",\"type\":\"HOME\"}"))
                .andReturn().getResponse().getContentAsString();
        String order = mvc.perform(as(token, post("/api/orders")).content("{\"addressId\":" + JsonPath.read(address, "$.id")
                + ",\"paymentMethod\":\"COD\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        if (delivered) {
            String admin = login("admin@cartnest.in", "Admin@123");
            String number = JsonPath.read(order, "$.orderNumber");
            for (String step : new String[] { "CONFIRMED", "SHIPPED", "DELIVERED" }) {
                mvc.perform(as(admin, put("/api/admin/orders/" + number + "/status")).content("{\"status\":\"" + step + "\"}"))
                        .andExpect(status().isOk());
            }
        }
        return token;
    }

    private static String review(int rating, String comment) {
        return "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}";
    }

    @Test
    void onlyCustomersWhoReceivedTheProductCanReview() throws Exception {
        mvc.perform(post("/api/products/" + PRODUCT + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(review(5, "Lovely glass, very sturdy"))).andExpect(status().isUnauthorized());

        String notDelivered = customerWithOrder(false);
        mvc.perform(as(notDelivered, get("/api/products/" + PRODUCT + "/reviews/mine")))
                .andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.reason").value("You can review a product once it has been delivered to you"));
        mvc.perform(as(notDelivered, post("/api/products/" + PRODUCT + "/reviews")).content(review(5, "Lovely glass, very sturdy")))
                .andExpect(status().isForbidden());

        String admin = login("admin@cartnest.in", "Admin@123");
        mvc.perform(as(admin, post("/api/products/" + PRODUCT + "/reviews")).content(review(5, "Lovely glass, very sturdy")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeliveredProductCanBeReviewedOnceAndTheRatingIsRecalculated() throws Exception {
        String before = mvc.perform(get("/api/products/" + PRODUCT)).andReturn().getResponse().getContentAsString();
        int countBefore = JsonPath.read(before, "$.ratingSummary.count");

        String token = customerWithOrder(true);
        mvc.perform(as(token, get("/api/products/" + PRODUCT + "/reviews/mine"))).andExpect(jsonPath("$.canReview").value(true));

        mvc.perform(as(token, post("/api/products/" + PRODUCT + "/reviews")).content(review(1, "Cracked on the first day.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(1))
                .andExpect(jsonPath("$.reviewerName").value("Test User"));

        mvc.perform(get("/api/products/" + PRODUCT))
                .andExpect(jsonPath("$.ratingSummary.count").value(countBefore + 1))
                .andExpect(jsonPath("$.topReviews[0].comment").value("Cracked on the first day."));
        mvc.perform(get("/api/products?q=glass"))
                .andExpect(jsonPath("$.items[?(@.slug == 'glass')].ratingCount").value(countBefore + 1));

        mvc.perform(as(token, post("/api/products/" + PRODUCT + "/reviews")).content(review(5, "Second thoughts, it is great")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already reviewed this product"));
        mvc.perform(as(token, get("/api/products/" + PRODUCT + "/reviews/mine")))
                .andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.review.rating").value(1));
    }

    @Test
    void reviewsAreValidated() throws Exception {
        String token = customerWithOrder(true);
        mvc.perform(as(token, post("/api/products/" + PRODUCT + "/reviews")).content(review(6, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").value("Rating must be between 1 and 5"))
                .andExpect(jsonPath("$.fieldErrors.comment").value("The review must be 10 to 1000 characters"));
    }
}
