package com.cartnest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class AdminApiTests extends ApiTestSupport {

    private String admin;
    private final List<Integer> createdProducts = new ArrayList<>();

    @BeforeEach
    void logInAsAdmin() throws Exception {
        admin = login("admin@cartnest.in", "Admin@123");
    }

    /** Products made by these tests are switched off again, so the catalogue tests still see the original 194. */
    @AfterEach
    void deactivateCreatedProducts() throws Exception {
        for (Integer id : createdProducts) {
            mvc.perform(as(admin, patch("/api/admin/products/" + id + "/active")).content("{\"active\":false}"));
        }
    }

    private String createProduct(String body) throws Exception {
        String json = mvc.perform(as(admin, post("/api/admin/products")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        createdProducts.add(JsonPath.read(json, "$.id"));
        return json;
    }

    private Integer categoryId(String slug) throws Exception {
        String json = mvc.perform(get("/api/categories/" + slug)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /** A sized T-shirt with S/M/L; the SKU prefix keeps every test's SKUs unique. */
    private String tshirt(String title, String skuPrefix, int categoryId) {
        return "{\"title\":\"" + title + "\",\"description\":\"Soft cotton T-shirt\",\"categoryId\":" + categoryId + ","
                + "\"variantType\":\"SIZE\",\"images\":[\"https://cdn.example.com/tee/1.jpg\",\"https://cdn.example.com/tee/2.jpg\"],"
                + "\"variants\":["
                + "{\"sku\":\"" + skuPrefix + "-S\",\"label\":\"S\",\"price\":599,\"mrp\":999,\"stock\":5},"
                + "{\"sku\":\"" + skuPrefix + "-M\",\"label\":\"M\",\"price\":549,\"mrp\":999,\"stock\":8},"
                + "{\"sku\":\"" + skuPrefix + "-L\",\"label\":\"L\",\"price\":599,\"mrp\":999,\"stock\":0}],"
                + "\"specs\":[{\"name\":\"Fabric\",\"value\":\"100% cotton\"}],\"tags\":[\"T-Shirt\",\"cotton\"]}";
    }

    private static String sku() {
        return "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void onlyAdminsReachTheAdminApi() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(as(newCustomer(), get("/api/admin/dashboard"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers").isNumber())
                .andExpect(jsonPath("$.activeProducts").isNumber())
                .andExpect(jsonPath("$.salesLast14Days", hasSize(14)))
                .andExpect(jsonPath("$.ordersByStatus.PLACED").isNumber());
    }

    @Test
    void createdProductAppearsInTheShopWithPricesFromItsCheapestVariant() throws Exception {
        String json = createProduct(tshirt("Classic Crew Tee", sku(), categoryId("mens-shirts")));
        mvc.perform(as(admin, get("/api/admin/products/" + JsonPath.read(json, "$.id"))))
                .andExpect(jsonPath("$.slug").value(startsWith("classic-crew-tee")))
                .andExpect(jsonPath("$.price").value(549.0))
                .andExpect(jsonPath("$.mrp").value(999.0))
                .andExpect(jsonPath("$.discountPercent").value(45))
                .andExpect(jsonPath("$.tags", contains("cotton", "t-shirt")));
        String slug = JsonPath.read(json, "$.slug");
        Integer id = JsonPath.read(json, "$.id");

        mvc.perform(get("/api/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breadcrumb[*].name", contains("Men's Fashion", "Shirts")))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.variants[*].label", contains("S", "M", "L")));
        mvc.perform(get("/api/products?q=classic crew tee"))
                .andExpect(jsonPath("$.items[*].slug", hasItem(slug)));

        // Deactivated: gone from the shop, still visible to the admin
        mvc.perform(as(admin, patch("/api/admin/products/" + id + "/active")).content("{\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/products/" + slug)).andExpect(status().isNotFound());
        mvc.perform(as(admin, get("/api/admin/products?active=false&q=classic crew tee")))
                .andExpect(jsonPath("$.items[*].id", hasItem(id)));
    }

    @Test
    void productRulesAreEnforced() throws Exception {
        int shirts = categoryId("mens-shirts");
        String prefix = sku();
        mvc.perform(as(admin, post("/api/admin/products")).content(tshirt("Bad Category Tee", prefix, categoryId("men"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Products can only be placed in a sub-category")));
        mvc.perform(as(admin, post("/api/admin/products"))
                        .content(tshirt("Cheap MRP Tee", prefix, shirts).replace("\"mrp\":999,\"stock\":8", "\"mrp\":500,\"stock\":8")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variant 'M': MRP cannot be lower than the price"));
        mvc.perform(as(admin, post("/api/admin/products"))
                        .content(tshirt("Twin Label Tee", prefix, shirts).replace("\"label\":\"L\"", "\"label\":\"m\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variant label 'm' is used twice"));
        mvc.perform(as(admin, post("/api/admin/products"))
                        .content(tshirt("Existing SKU Tee", prefix, shirts).replace(prefix + "-S", "SMA-APP-IPH-123-128GB")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SKU SMA-APP-IPH-123-128GB is already used by another product"));
        mvc.perform(as(admin, post("/api/admin/products")).content("{\"title\":\"\",\"images\":[\"not a url\"],\"variants\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.variants").value("At least one variant is required"))
                .andExpect(jsonPath("$.fieldErrors.categoryId").value("categoryId is required"));
    }

    @Test
    void updatingVariantsRecalculatesPricesAndProtectsOrderedVariants() throws Exception {
        String prefix = sku();
        String created = createProduct(tshirt("Editable Tee", prefix, categoryId("mens-shirts")));
        Integer id = JsonPath.read(created, "$.id");
        Integer s = JsonPath.read(created, "$.variants[0].id");
        Integer m = JsonPath.read(created, "$.variants[1].id");
        String slug = JsonPath.read(created, "$.slug");

        // A customer buys size M
        String customer = newCustomer();
        mvc.perform(as(customer, post("/api/cart/items")).content("{\"variantId\":" + m + "}"));
        String address = mvc.perform(as(customer, post("/api/addresses")).content("{\"fullName\":\"A B\",\"phone\":\"9876543210\","
                + "\"line1\":\"1 Road\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\",\"type\":\"HOME\"}"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(as(customer, post("/api/orders")).content("{\"addressId\":" + JsonPath.read(address, "$.id")
                + ",\"paymentMethod\":\"CARD\"}")).andExpect(status().isCreated());

        // Keep S (cheaper now), drop M: refused, M has been ordered
        String withoutM = "{\"title\":\"Editable Tee\",\"description\":\"Soft cotton T-shirt\",\"categoryId\":" + categoryId("mens-shirts")
                + ",\"variantType\":\"SIZE\",\"images\":[\"https://cdn.example.com/tee/1.jpg\"],\"variants\":["
                + "{\"id\":" + s + ",\"sku\":\"" + prefix + "-S\",\"label\":\"S\",\"price\":399,\"mrp\":999,\"stock\":5},"
                + "{\"sku\":\"" + prefix + "-XL\",\"label\":\"XL\",\"price\":649,\"mrp\":999,\"stock\":3}]}";
        mvc.perform(as(admin, put("/api/admin/products/" + id)).content(withoutM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Variant 'M' has been ordered and cannot be removed")));

        String keepM = withoutM.replace("\"variants\":[", "\"variants\":[{\"id\":" + m + ",\"sku\":\"" + prefix
                + "-M\",\"label\":\"M\",\"price\":549,\"mrp\":999,\"stock\":0},");
        mvc.perform(as(admin, put("/api/admin/products/" + id)).content(keepM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[*].label", contains("M", "S", "XL")))
                .andExpect(jsonPath("$.price").value(399.0))
                .andExpect(jsonPath("$.images", hasSize(1)));
        mvc.perform(get("/api/products/" + slug)).andExpect(jsonPath("$.price").value(399.0));

        mvc.perform(as(admin, patch("/api/admin/variants/" + m + "/stock")).content("{\"stock\":25}"))
                .andExpect(jsonPath("$.stock").value(25));
        mvc.perform(as(admin, patch("/api/admin/variants/" + m + "/stock")).content("{\"stock\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersMoveForwardOneStepAndCodIsPaidOnDelivery() throws Exception {
        String customer = newCustomer();
        String product = mvc.perform(get("/api/products/plate")).andReturn().getResponse().getContentAsString();
        mvc.perform(as(customer, post("/api/cart/items")).content("{\"variantId\":" + JsonPath.read(product, "$.variants[0].id") + "}"));
        String address = mvc.perform(as(customer, post("/api/addresses")).content("{\"fullName\":\"A B\",\"phone\":\"9876543210\","
                + "\"line1\":\"1 Road\",\"city\":\"Indore\",\"state\":\"Madhya Pradesh\",\"pincode\":\"452001\",\"type\":\"HOME\"}"))
                .andReturn().getResponse().getContentAsString();
        String order = mvc.perform(as(customer, post("/api/orders")).content("{\"addressId\":" + JsonPath.read(address, "$.id")
                        + ",\"paymentMethod\":\"COD\"}"))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(order, "$.orderNumber");

        mvc.perform(as(admin, get("/api/admin/orders?status=PLACED&q=" + number)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].city").value("Indore"));
        mvc.perform(as(admin, put("/api/admin/orders/" + number + "/status")).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An order that is PLACED cannot be moved to DELIVERED; the next step is CONFIRMED"));

        mvc.perform(as(admin, put("/api/admin/orders/" + number + "/status")).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(as(admin, put("/api/admin/orders/" + number + "/status")).content("{\"status\":\"SHIPPED\",\"note\":\"Blue Dart AWB 1234\"}"))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        // Too late for the customer to cancel
        mvc.perform(as(customer, post("/api/orders/" + number + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This order has already shipped and can no longer be cancelled"));
        mvc.perform(as(admin, put("/api/admin/orders/" + number + "/status")).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.timeline[*].status", contains("PLACED", "CONFIRMED", "SHIPPED", "DELIVERED")))
                .andExpect(jsonPath("$.timeline[2].note").value("Blue Dart AWB 1234"));

        // the customer sees the same timeline
        mvc.perform(as(customer, get("/api/orders/" + number))).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void adminCanCancelBeforeShipping() throws Exception {
        String customer = newCustomer();
        String product = mvc.perform(get("/api/products/plate")).andReturn().getResponse().getContentAsString();
        mvc.perform(as(customer, post("/api/cart/items")).content("{\"variantId\":" + JsonPath.read(product, "$.variants[0].id") + "}"));
        String address = mvc.perform(as(customer, post("/api/addresses")).content("{\"fullName\":\"A B\",\"phone\":\"9876543210\","
                + "\"line1\":\"1 Road\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\",\"type\":\"HOME\"}"))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(mvc.perform(as(customer, post("/api/orders")).content("{\"addressId\":"
                + JsonPath.read(address, "$.id") + ",\"paymentMethod\":\"UPI\",\"upiId\":\"ab@okbank\"}"))
                .andReturn().getResponse().getContentAsString(), "$.orderNumber");
        mvc.perform(as(admin, post("/api/admin/orders/" + number + "/cancel")).content("{\"reason\":\"Item damaged in warehouse\"}"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.cancelReason").value("Item damaged in warehouse"));
    }

    @Test
    void categoriesAndBrandsCannotBeDeletedWhileInUse() throws Exception {
        Integer men = categoryId("men");
        String created = mvc.perform(as(admin, post("/api/admin/categories")).content("{\"name\":\"Footwear\",\"parentId\":" + men + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // "footwear" is still free, so it is used as is; the second one is prefixed with its parent's slug
        Integer first = JsonPath.read(created, "$.id");
        String second = mvc.perform(as(admin, post("/api/admin/categories")).content("{\"name\":\"Footwear\",\"parentId\":" + categoryId("women") + "}"))
                .andExpect(jsonPath("$.slug").value("women-footwear"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(as(admin, delete("/api/admin/categories/" + JsonPath.read(second, "$.id")))).andExpect(status().isNoContent());
        mvc.perform(as(admin, post("/api/admin/categories")).content("{\"name\":\"Too Deep\",\"parentId\":" + first + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(admin, post("/api/admin/categories")).content("{\"name\":\"Under A Leaf\",\"parentId\":" + categoryId("smartphones") + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(admin, delete("/api/admin/categories/" + categoryId("smartphones"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("'Smartphones' still has products; move or deactivate them first"));
        mvc.perform(as(admin, delete("/api/admin/categories/" + men))).andExpect(status().isConflict());
        mvc.perform(as(admin, delete("/api/admin/categories/" + first))).andExpect(status().isNoContent());

        mvc.perform(as(admin, post("/api/admin/brands")).content("{\"name\":\"apple\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Brand 'apple' already exists"));
        String brand = mvc.perform(as(admin, post("/api/admin/brands")).content("{\"name\":\"Brand " + sku() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(as(admin, delete("/api/admin/brands/" + JsonPath.read(brand, "$.id")))).andExpect(status().isNoContent());
    }

    @Test
    void couponsCanBeCreatedAndSwitchedOff() throws Exception {
        String code = "TEST" + sku().substring(1, 7);
        String body = "{\"code\":\"" + code.toLowerCase() + "\",\"description\":\"₹300 off\",\"discountType\":\"FLAT\","
                + "\"discountValue\":300,\"minOrderAmount\":0,\"validFrom\":\"2026-01-01T00:00:00\","
                + "\"validUntil\":\"2030-01-01T00:00:00\",\"perUserLimit\":1}";
        String created = mvc.perform(as(admin, post("/api/admin/coupons")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(as(admin, post("/api/admin/coupons")).content(body)).andExpect(status().isConflict());
        mvc.perform(as(admin, post("/api/admin/coupons")).content(body.replace("\"FLAT\"", "\"PERCENT\"").replace(code.toLowerCase(), "OTHER" + code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A percentage discount can be at most 90%"));

        String customer = newCustomer();
        String plate = mvc.perform(get("/api/products/plate")).andReturn().getResponse().getContentAsString();
        mvc.perform(as(customer, post("/api/cart/items")).content("{\"variantId\":" + JsonPath.read(plate, "$.variants[0].id") + ",\"quantity\":2}"));
        mvc.perform(as(customer, post("/api/checkout/preview")).content("{\"couponCode\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.couponDiscount").value(300.0));

        mvc.perform(as(admin, patch("/api/admin/coupons/" + JsonPath.read(created, "$.id") + "/active")).content("{\"active\":false}"))
                .andExpect(jsonPath("$.state").value("DISABLED"));
        mvc.perform(as(customer, post("/api/checkout/preview")).content("{\"couponCode\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.couponMessage").value("Coupon " + code + " is no longer available"));
    }

    @Test
    void disablingACustomerLocksThemOut() throws Exception {
        String email = "shopper-" + UUID.randomUUID() + "@example.com";
        String customer = register(email);
        String json = mvc.perform(as(admin, get("/api/admin/customers?q=" + email)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].orderCount").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(json, "$.items[0].id");

        mvc.perform(as(admin, patch("/api/admin/customers/" + id + "/enabled")).content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(as(customer, get("/api/cart"))).andExpect(status().isUnauthorized());

        mvc.perform(as(admin, patch("/api/admin/customers/1/enabled")).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(admin, get("/api/admin/customers?q=admin@cartnest.in")))
                .andExpect(jsonPath("$.items", not(hasItem(org.hamcrest.Matchers.anything()))));
    }
}
