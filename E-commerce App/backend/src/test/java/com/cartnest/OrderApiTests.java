package com.cartnest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cartnest.entity.ProductVariant;
import com.cartnest.repository.ProductVariantRepository;
import com.jayway.jsonpath.JsonPath;

class OrderApiTests extends ApiTestSupport {

    @Autowired
    private ProductVariantRepository variantRepository;

    private long variantId(String slug) throws Exception {
        String json = mvc.perform(get("/api/products/" + slug)).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.variants[0].id")).longValue();
    }

    private int stock(long variantId) {
        return variantRepository.findById(variantId).orElseThrow().getStock();
    }

    private void addToCart(String token, long variantId, int quantity) throws Exception {
        mvc.perform(as(token, post("/api/cart/items")).content("{\"variantId\":" + variantId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private Integer addAddress(String token) throws Exception {
        String json = mvc.perform(as(token, post("/api/addresses")).content("{\"fullName\":\"Riya Verma\","
                        + "\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Pune\",\"state\":\"Maharashtra\","
                        + "\"pincode\":\"411001\",\"type\":\"HOME\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String order(Integer addressId, String method, String extra) {
        return "{\"addressId\":" + addressId + ",\"paymentMethod\":\"" + method + "\"" + extra + "}";
    }

    @Test
    void cashOnDeliveryOrderTakesStockAndEmptiesTheCart() throws Exception {
        String token = newCustomer();
        long spatula = variantId("bamboo-spatula");
        long blender = variantId("boxed-blender");
        int spatulaStock = stock(spatula);
        addToCart(token, spatula, 2);
        addToCart(token, blender, 1);
        Integer address = addAddress(token);

        // The preview picks the default address and shows the totals that the order will have
        String preview = mvc.perform(as(token, post("/api/checkout/preview")).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.id").value(address))
                .andExpect(jsonPath("$.checkoutReady").value(true))
                .andReturn().getResponse().getContentAsString();
        Double expectedTotal = JsonPath.read(preview, "$.grandTotal");

        String placed = mvc.perform(as(token, post("/api/orders")).content(order(address, "COD", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(startsWith("CN")))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.shippingAddress.city").value("Pune"))
                .andExpect(jsonPath("$.grandTotal").value(expectedTotal))
                .andExpect(jsonPath("$.timeline[0].status").value("PLACED"))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(placed, "$.orderNumber");

        assertThat(stock(spatula)).isEqualTo(spatulaStock - 2);
        mvc.perform(as(token, get("/api/cart"))).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(as(token, get("/api/orders")))
                .andExpect(jsonPath("$.items[0].orderNumber").value(number))
                .andExpect(jsonPath("$.items[0].itemCount").value(3));
        mvc.perform(as(token, get("/api/orders/" + number))).andExpect(jsonPath("$.items[0].quantity").exists());
        // nobody else can see it
        mvc.perform(as(newCustomer(), get("/api/orders/" + number))).andExpect(status().isNotFound());
    }

    @Test
    void onlinePaymentsArePaidAndADeclinedPaymentChangesNothing() throws Exception {
        String token = newCustomer();
        long stove = variantId("electric-stove");
        int before = stock(stove);
        addToCart(token, stove, 1);
        Integer address = addAddress(token);

        mvc.perform(as(token, post("/api/orders")).content(order(address, "UPI", ",\"upiId\":\"riya@fail\"")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value("Payment was declined by your bank. You have not been charged"));
        assertThat(stock(stove)).isEqualTo(before);
        mvc.perform(as(token, get("/api/cart"))).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(as(token, get("/api/orders"))).andExpect(jsonPath("$.totalItems").value(0));

        mvc.perform(as(token, post("/api/orders")).content(order(address, "UPI", ",\"upiId\":\"riya@okbank\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentReference").value(startsWith("PAY")));
        assertThat(stock(stove)).isEqualTo(before - 1);
    }

    @Test
    void couponsFollowTheirRulesAndCancellingGivesTheUseBack() throws Exception {
        String token = newCustomer();
        Integer address = addAddress(token);
        addToCart(token, variantId("bamboo-spatula"), 1);

        mvc.perform(as(token, post("/api/checkout/preview")).content("{\"couponCode\":\"welcome10\"}"))
                .andExpect(jsonPath("$.couponDiscount").value(0))
                .andExpect(jsonPath("$.couponMessage").value(startsWith("Add items worth ₹")));
        mvc.perform(as(token, post("/api/checkout/preview")).content("{\"couponCode\":\"NEWYEAR26\"}"))
                .andExpect(jsonPath("$.couponMessage").value("Coupon NEWYEAR26 has expired"));
        mvc.perform(as(token, post("/api/checkout/preview")).content("{\"couponCode\":\"NOPE\"}"))
                .andExpect(jsonPath("$.couponMessage").value("Coupon 'NOPE' does not exist"));

        addToCart(token, variantId("microwave-oven"), 1);
        String preview = mvc.perform(as(token, post("/api/checkout/preview")).content("{\"couponCode\":\"welcome10\"}"))
                .andExpect(jsonPath("$.couponCode").value("WELCOME10"))
                // 10% of more than ₹5,000 is capped at ₹500
                .andExpect(jsonPath("$.couponDiscount").value(500.0))
                .andReturn().getResponse().getContentAsString();
        Double itemTotal = JsonPath.read(preview, "$.itemTotal");

        String placed = mvc.perform(as(token, post("/api/orders")).content(order(address, "CARD", ",\"couponCode\":\"WELCOME10\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponDiscount").value(500.0))
                .andExpect(jsonPath("$.grandTotal").value(itemTotal - 500))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(placed, "$.orderNumber");

        // WELCOME10 is once per customer
        addToCart(token, variantId("microwave-oven"), 1);
        mvc.perform(as(token, post("/api/orders")).content(order(address, "CARD", ",\"couponCode\":\"WELCOME10\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have already used coupon WELCOME10"));
        mvc.perform(as(token, get("/api/coupons")))
                .andExpect(jsonPath("$[?(@.code == 'WELCOME10')]", hasSize(0)));

        mvc.perform(as(token, post("/api/orders/" + number + "/cancel")).content("{}")).andExpect(status().isOk());
        mvc.perform(as(token, post("/api/checkout/preview")).content("{\"couponCode\":\"WELCOME10\"}"))
                .andExpect(jsonPath("$.couponDiscount").value(500.0));
    }

    @Test
    void cancellingRestocksRefundsAndCanOnlyHappenOnce() throws Exception {
        String token = newCustomer();
        long blender = variantId("hand-blender");
        int before = stock(blender);
        addToCart(token, blender, 2);
        String placed = mvc.perform(as(token, post("/api/orders")).content(order(addAddress(token), "CARD", "")))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(placed, "$.orderNumber");
        assertThat(stock(blender)).isEqualTo(before - 2);

        mvc.perform(as(token, post("/api/orders/" + number + "/cancel")).content("{\"reason\":\"Ordered by mistake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.cancelReason").value("Ordered by mistake"))
                .andExpect(jsonPath("$.timeline[*].status", org.hamcrest.Matchers.contains("PLACED", "CANCELLED")))
                .andExpect(jsonPath("$.cancellable").value(false));
        assertThat(stock(blender)).isEqualTo(before);

        mvc.perform(as(token, post("/api/orders/" + number + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This order is already cancelled"));
    }

    @Test
    void checkoutGuards() throws Exception {
        String token = newCustomer();
        Integer address = addAddress(token);
        mvc.perform(as(token, post("/api/orders")).content(order(address, "COD", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Your cart is empty"));

        // ₹93,499 is above the cash-on-delivery limit
        addToCart(token, variantId("iphone-13-pro"), 1);
        mvc.perform(as(token, post("/api/orders")).content(order(address, "COD", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Cash on delivery is available for orders up to")));
        mvc.perform(as(token, post("/api/checkout/preview")).content("{}"))
                .andExpect(jsonPath("$.codAvailable").value(false));

        // another customer's address
        mvc.perform(as(token, post("/api/orders")).content(order(addAddress(newCustomer()), "CARD", "")))
                .andExpect(status().isNotFound());
        mvc.perform(as(token, post("/api/orders")).content("{\"paymentMethod\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest());

        String admin = login("admin@cartnest.in", "Admin@123");
        mvc.perform(as(admin, post("/api/orders")).content(order(address, "COD", ""))).andExpect(status().isForbidden());
    }

    @Test
    void stockThatRanOutAfterAddingToCartBlocksTheOrder() throws Exception {
        String token = newCustomer();
        Integer address = addAddress(token);
        long tray = variantId("tray");
        addToCart(token, tray, 3);
        ProductVariant variant = variantRepository.findById(tray).orElseThrow();
        int original = variant.getStock();
        try {
            variant.setStock(2);
            variantRepository.save(variant);
            mvc.perform(as(token, post("/api/orders")).content(order(address, "CARD", "")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Only 2 of Tray left"));
            assertThat(stock(tray)).isEqualTo(2);
        } finally {
            variant.setStock(original);
            variantRepository.save(variant);
        }
    }
}
