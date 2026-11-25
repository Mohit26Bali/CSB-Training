package com.cartnest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** The public catalogue API, run against the imported catalogue (194 products in 52 categories). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class CatalogApiTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void categoryTreeHasTenDepartmentsCoveringEveryProduct() throws Exception {
        String json = mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[0].children", hasSize(9)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> counts = JsonPath.read(json, "$[*].productCount");
        assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(194);
    }

    @Test
    void categoryDetailHasBreadcrumbAndCount() throws Exception {
        mvc.perform(get("/api/categories/smartphones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(16))
                .andExpect(jsonPath("$.breadcrumb[*].name", contains("Electronics", "Smartphones")));
        mvc.perform(get("/api/categories/no-such-category")).andExpect(status().isNotFound());
    }

    @Test
    void departmentListingIncludesItsSubCategories() throws Exception {
        mvc.perform(get("/api/products?category=electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(38))
                .andExpect(jsonPath("$.items", hasSize(24)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.facets.categories[0].value").value("smartphones"));
    }

    @Test
    void brandFilterAndPriceSort() throws Exception {
        String json = mvc.perform(get("/api/products?category=smartphones&brand=apple&sort=price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[*].brand", everyItem(org.hamcrest.Matchers.is("Apple"))))
                // the facets still offer the other brands of the category
                .andExpect(jsonPath("$.facets.brands[*].value", hasItem("samsung")))
                .andReturn().getResponse().getContentAsString();
        List<Double> prices = JsonPath.read(json, "$.items[*].price");
        assertThat(prices).isSorted();
    }

    @Test
    void priceRatingDiscountAndStockFiltersCombine() throws Exception {
        String json = mvc.perform(get("/api/products?minPrice=1000&maxPrice=50000&minRating=4&minDiscount=10"
                        + "&inStock=true&size=60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].rating", everyItem(greaterThanOrEqualTo(4.0))))
                .andExpect(jsonPath("$.items[*].discountPercent", everyItem(greaterThanOrEqualTo(10))))
                .andExpect(jsonPath("$.items[*].inStock", everyItem(org.hamcrest.Matchers.is(true))))
                .andReturn().getResponse().getContentAsString();
        List<Double> prices = JsonPath.read(json, "$.items[*].price");
        assertThat(prices).isNotEmpty().allSatisfy(p -> assertThat(p).isBetween(1000.0, 50000.0));
    }

    @Test
    void sizeFilterOnlyShowsProductsWithThatSizeInStock() throws Exception {
        mvc.perform(get("/api/products?category=mens-footwear"))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.facets.sizes[*].value", contains("UK 7", "UK 8", "UK 9", "UK 10", "UK 11")));
        // one of the five has sold out of UK 11
        mvc.perform(get("/api/products?category=mens-footwear&sizes=UK 11"))
                .andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void textSearchMatchesEveryWordAndRanksBestMatchesFirst() throws Exception {
        mvc.perform(get("/api/products?q=apple phone&size=4"))
                .andExpect(jsonPath("$.sort").value("relevance"))
                .andExpect(jsonPath("$.items[*].title", everyItem(startsWith("iPhone"))));
        mvc.perform(get("/api/products?q=cricket bat"))
                .andExpect(jsonPath("$.items[0].title").value("Cricket Bat"));
        mvc.perform(get("/api/products?q=zzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.facets.price").doesNotExist());
    }

    @Test
    void productDetailHasVariantsImagesBreadcrumbReviewsAndSimilarProducts() throws Exception {
        mvc.perform(get("/api/products/iphone-13-pro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand.name").value("Apple"))
                .andExpect(jsonPath("$.variantType").value("STORAGE"))
                .andExpect(jsonPath("$.variants[*].label", contains("128 GB", "256 GB", "512 GB")))
                .andExpect(jsonPath("$.price").value(93499.0))
                .andExpect(jsonPath("$.breadcrumb[*].slug", contains("electronics", "smartphones")))
                .andExpect(jsonPath("$.images", not(hasSize(0))))
                .andExpect(jsonPath("$.ratingSummary.count").value(3))
                .andExpect(jsonPath("$.similar", hasSize(8)))
                .andExpect(jsonPath("$.similar[*].slug", not(hasItem("iphone-13-pro"))));
    }

    @Test
    void reviewsArePagedWithAFiveStarBreakdown() throws Exception {
        String json = mvc.perform(get("/api/products/iphone-13-pro/reviews?size=2&sort=highest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ratings = JsonPath.read(json, "$.items[*].rating");
        assertThat(ratings).isSortedAccordingTo((a, b) -> b - a);
        Integer total = JsonPath.read(json, "$.summary.count");
        List<Integer> stars = JsonPath.read(json, "$.summary.distribution.*");
        assertThat(stars).hasSize(5);
        assertThat(stars.stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
    }

    @Test
    void suggestionsAndHomePage() throws Exception {
        mvc.perform(get("/api/search/suggest?q=sams"))
                .andExpect(jsonPath("$.brands[0].name").value("Samsung"))
                .andExpect(jsonPath("$.products[*].title", hasItem(startsWith("Samsung"))));
        mvc.perform(get("/api/search/suggest?q=a"))
                .andExpect(jsonPath("$.products", hasSize(0)));
        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(10)))
                .andExpect(jsonPath("$.sections[0].title").value("Deals of the Day"))
                .andExpect(jsonPath("$.sections[0].products[*].discountPercent", everyItem(greaterThanOrEqualTo(15))));
    }

    @Test
    void invalidQueriesGetClearErrors() throws Exception {
        mvc.perform(get("/api/products?sort=cheapest&size=500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").exists())
                .andExpect(jsonPath("$.fieldErrors.size").value("size must be between 1 and 60"));
        mvc.perform(get("/api/products?minPrice=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minPrice").value("invalid value 'abc'"));
        mvc.perform(get("/api/products?minPrice=500&maxPrice=100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("minPrice cannot be greater than maxPrice"));
        mvc.perform(get("/api/products/no-such-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product 'no-such-product' not found"));
        mvc.perform(get("/api/products/iphone-13-pro/reviews?sort=best"))
                .andExpect(status().isBadRequest());
    }
}
