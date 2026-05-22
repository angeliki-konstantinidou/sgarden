package com.sgarden.service;

import com.sgarden.dto.ProductRequest;
import com.sgarden.dto.ProductStatsResponse;
import com.sgarden.model.Product;
import com.sgarden.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Product> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(String id) {
        log.info("Fetching product: {}", id);
        return productRepository.findById(id);
    }

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock() != null ? request.getStock() : 0);
        log.info("Creating product: {}", request.getName());
        return productRepository.save(product);
    }

    public Optional<Product> updateProduct(String id, ProductRequest request) {
        return productRepository.findById(id).map(product -> {
            if (request.getName() != null) product.setName(request.getName());
            if (request.getDescription() != null) product.setDescription(request.getDescription());
            if (request.getCategory() != null) product.setCategory(request.getCategory());
            if (request.getPrice() != null) product.setPrice(request.getPrice());
            if (request.getStock() != null) product.setStock(request.getStock());
            log.info("Updating product: {}", id);
            return productRepository.save(product);
        });
    }

    public ProductStatsResponse getProductStats() {
        List<Product> all = productRepository.findAll();
        long totalCount = all.size();

        DoubleSummaryStatistics priceStats = all.stream()
                .filter(p -> p.getPrice() != null)
                .mapToDouble(Product::getPrice)
                .summaryStatistics();

        Map<String, Long> categoryCount = all.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

        double averagePrice = priceStats.getCount() > 0 ? priceStats.getAverage() : 0.0;
        Double minPrice = priceStats.getCount() > 0 ? priceStats.getMin() : null;
        Double maxPrice = priceStats.getCount() > 0 ? priceStats.getMax() : null;

        return new ProductStatsResponse(totalCount, averagePrice, minPrice, maxPrice, categoryCount);
    }

    public List<Product> searchProducts(String q, String category, Double minPrice, Double maxPrice) {
        boolean noFilters = (q == null || q.isBlank())
                && (category == null || category.isBlank())
                && minPrice == null
                && maxPrice == null;
        if (noFilters) {
            throw new IllegalArgumentException("At least one search parameter is required: q, category, minPrice, or maxPrice");
        }

        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");
        }

        Query query = new Query();

        if (q != null && !q.isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().caseSensitive(false).matching(q));
        }

        List<Criteria> criteria = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            criteria.add(Criteria.where("category").is(category));
        }
        if (minPrice != null) {
            criteria.add(Criteria.where("price").gte(minPrice));
        }
        if (maxPrice != null) {
            criteria.add(Criteria.where("price").lte(maxPrice));
        }
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }

        log.info("Searching products — q={}, category={}, minPrice={}, maxPrice={}", q, category, minPrice, maxPrice);
        return mongoTemplate.find(query, Product.class);
    }

    public boolean deleteProduct(String id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            log.info("Deleted product: {}", id);
            return true;
        }
        return false;
    }
}
