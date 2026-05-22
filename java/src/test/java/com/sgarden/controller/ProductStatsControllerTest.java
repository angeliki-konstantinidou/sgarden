package com.sgarden.controller;

import com.sgarden.model.Product;
import com.sgarden.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductStatsControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    /*
     * Seed: 6 products across 3 explicit categories + 1 null-category product.
     * null-category is intentional — it verifies the categoryCount invariant
     * by ensuring it lands in "uncategorized" rather than being silently dropped.
     *
     *   seeds:         1  (5.99)
     *   tools:         2  (12.99, 24.99)
     *   fertilizers:   2  (8.99, 15.99)
     *   uncategorized: 1  (9.99)
     *   ─────────────────
     *   total:         6  (min=5.99, max=24.99, avg≈13.16)
     */
    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productRepository.saveAll(List.of(
            product("Rose Seeds",        "seeds",        5.99),
            product("Gardening Gloves",  "tools",       12.99),
            product("Pruning Shears",    "tools",       24.99),
            product("Tomato Fertilizer", "fertilizers",  8.99),
            product("Orchid Mix",        "fertilizers", 15.99),
            product("Mystery Box",       null,           9.99)
        ));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void getStats_returns200() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/products/stats"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getStats_totalCountIsGreaterThanZero() {
        Map<?, ?> body = fetchStats();
        long totalCount = toLong(body.get("totalCount"));
        assertThat(totalCount).isGreaterThan(0);
    }

    @Test
    void getStats_averagePriceIsPositive() {
        Map<?, ?> body = fetchStats();
        double averagePrice = toDouble(body.get("averagePrice"));
        assertThat(averagePrice).isGreaterThan(0.0);
    }

    @Test
    void getStats_maxPriceIsAtLeastMinPrice() {
        Map<?, ?> body = fetchStats();
        double minPrice = toDouble(body.get("minPrice"));
        double maxPrice = toDouble(body.get("maxPrice"));
        assertThat(minPrice).isGreaterThan(0.0);
        assertThat(maxPrice).isGreaterThanOrEqualTo(minPrice);
    }

    @Test
    void getStats_categoryCountContainsCategoryKeys() {
        Map<?, ?> body = fetchStats();
        Map<?, ?> categoryCount = (Map<?, ?>) body.get("categoryCount");
        assertThat(categoryCount)
            .isNotNull()
            .isNotEmpty()
            .containsKey("tools")
            .containsKey("fertilizers")
            .containsKey("seeds");
    }

    @Test
    void getStats_categoryCountSumsToTotalCount() {
        Map<?, ?> body = fetchStats();
        long totalCount = toLong(body.get("totalCount"));
        Map<?, ?> categoryCount = (Map<?, ?>) body.get("categoryCount");

        long sumOfCategories = categoryCount.values().stream()
            .mapToLong(v -> toLong(v))
            .sum();

        assertThat(sumOfCategories).isEqualTo(totalCount);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<?, ?> fetchStats() {
        return restTemplate.getForObject(url("/api/products/stats"), Map.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static long toLong(Object v) {
        return ((Number) v).longValue();
    }

    private static double toDouble(Object v) {
        return ((Number) v).doubleValue();
    }

    private static Product product(String name, String category, double price) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setStock(10);
        return p;
    }
}
