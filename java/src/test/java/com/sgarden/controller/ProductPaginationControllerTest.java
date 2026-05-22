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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductPaginationControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    /*
     * 10 products with deliberately alphabetical names and strictly increasing
     * prices so sorting assertions are deterministic without ties.
     *
     *   name (asc order)    price (asc order)
     *   Apple Seeds          3.99
     *   Bamboo Plant         5.49
     *   Cactus Mix           6.99
     *   Daisy Seeds          7.99
     *   Eucalyptus Oil       9.99
     *   Fern Soil           11.49
     *   Grape Fertilizer    12.99
     *   Herb Garden Kit     14.99
     *   Iris Bulbs          18.99
     *   Jasmine Seeds       22.99
     */
    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productRepository.saveAll(List.of(
            product("Apple Seeds",        3.99),
            product("Bamboo Plant",       5.49),
            product("Cactus Mix",         6.99),
            product("Daisy Seeds",        7.99),
            product("Eucalyptus Oil",     9.99),
            product("Fern Soil",         11.49),
            product("Grape Fertilizer",  12.99),
            product("Herb Garden Kit",   14.99),
            product("Iris Bulbs",        18.99),
            product("Jasmine Seeds",     22.99)
        ));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void getProducts_responseContainsRequiredFields() {
        Map<?, ?> body = fetch("/api/products?page=1&limit=5");

        assertThat(body).containsKeys("data", "page", "limit", "total");
        assertThat(body.get("data")).isInstanceOf(List.class);
        assertThat(((Number) body.get("page")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("limit")).intValue()).isEqualTo(5);
        assertThat(((List<?>) body.get("data"))).hasSize(5);
    }

    @Test
    void getProducts_page1AndPage2HaveNoOverlappingIds() {
        List<String> page1Ids = extractIds(fetch("/api/products?page=1&limit=5&sort=name&order=asc"));
        List<String> page2Ids = extractIds(fetch("/api/products?page=2&limit=5&sort=name&order=asc"));

        assertThat(page1Ids).hasSize(5);
        assertThat(page2Ids).hasSize(5);
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
    }

    @Test
    void getProducts_sortByPriceAscending_pricesAreNonDecreasing() {
        List<Double> prices = extractPrices(
            fetch("/api/products?page=1&limit=10&sort=price&order=asc"));

        assertThat(prices).isNotEmpty();
        assertThat(prices).isSortedAccordingTo(Double::compareTo);
    }

    @Test
    void getProducts_sortByPriceDescending_pricesAreNonIncreasing() {
        List<Double> prices = extractPrices(
            fetch("/api/products?page=1&limit=10&sort=price&order=desc"));

        assertThat(prices).isNotEmpty();
        assertThat(prices).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void getProducts_sortByNameAscending_namesAreInLexicographicOrder() {
        List<String> names = extractNames(
            fetch("/api/products?page=1&limit=10&sort=name&order=asc"));

        assertThat(names).isNotEmpty();
        assertThat(names).isSortedAccordingTo(String::compareTo);
    }

    @Test
    void getProducts_totalIsGreaterThanDataSizeWhenLimitIsSmall() {
        Map<?, ?> body = fetch("/api/products?page=1&limit=5");

        long total = ((Number) body.get("total")).longValue();
        int dataSize = ((List<?>) body.get("data")).size();

        assertThat(total).isGreaterThan(dataSize);
    }

    @Test
    void getProducts_pageWellBeyondData_returns200WithEmptyDataArray() {
        ResponseEntity<Map> response =
            restTemplate.getForEntity(url("/api/products?page=999&limit=5"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("data")).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<?, ?> fetch(String path) {
        return restTemplate.getForObject(url(path), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractIds(Map<?, ?> body) {
        return ((List<Map<?, ?>>) body.get("data")).stream()
            .map(p -> (String) p.get("id"))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Double> extractPrices(Map<?, ?> body) {
        return ((List<Map<?, ?>>) body.get("data")).stream()
            .map(p -> ((Number) p.get("price")).doubleValue())
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractNames(Map<?, ?> body) {
        return ((List<Map<?, ?>>) body.get("data")).stream()
            .map(p -> (String) p.get("name"))
            .collect(Collectors.toList());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Product product(String name, double price) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setStock(10);
        return p;
    }
}
