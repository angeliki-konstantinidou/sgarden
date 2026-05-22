package com.sgarden.service;

import com.sgarden.dto.PagedResponse;
import com.sgarden.dto.ProductRequest;
import com.sgarden.dto.ProductStatsResponse;
import com.sgarden.model.Product;
import com.sgarden.repository.ProductRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final Set<String> SORTABLE_FIELDS =
        Set.of("name", "price", "stock", "category", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public PagedResponse<Product> getAllProducts(int page, int limit, String sort, String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be 1 or greater");
        }
        if (!SORTABLE_FIELDS.contains(sort)) {
            throw new IllegalArgumentException(
                "Invalid sort field '" + sort + "'. Allowed: " + SORTABLE_FIELDS);
        }
        if (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("order must be 'asc' or 'desc'");
        }
        if (limit > 100) {
            throw new IllegalArgumentException("limit must not exceed 100");
        }

        Sort.Direction dir = order.equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;

        // page is 1-indexed externally; PageRequest is 0-indexed internally
        Page<Product> result = productRepository.findAll(
            PageRequest.of(page - 1, limit, Sort.by(dir, sort))
        );

        log.info("Fetching products page={}, limit={}, sort={} {}", page, limit, sort, order);
        return new PagedResponse<>(
            result.getContent(),
            page,
            limit,
            result.getTotalElements()
        );
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
        AggregationResults<Document> overviewResult = mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .count().as("totalCount")
                    .avg("price").as("averagePrice")
                    .min("price").as("minPrice")
                    .max("price").as("maxPrice")
            ),
            Product.class, Document.class
        );

        Document overview = overviewResult.getUniqueMappedResult();
        long totalCount     = overview != null ? toL(overview.get("totalCount"))   : 0L;
        double averagePrice = overview != null ? toD(overview.get("averagePrice")) : 0.0;
        Double minPrice     = overview != null ? toD(overview.get("minPrice"))     : null;
        Double maxPrice     = overview != null ? toD(overview.get("maxPrice"))     : null;

        Map<String, Long> categoryCount = mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.project()
                    .and(ConditionalOperators.ifNull("category").then("uncategorized")).as("category"),
                Aggregation.group("category").count().as("count")
            ),
            Product.class, Document.class
        ).getMappedResults().stream().collect(Collectors.toMap(
            doc -> doc.getString("_id"),
            doc -> toL(doc.get("count"))
        ));

        log.info("Computed product stats: totalCount={}", totalCount);
        return new ProductStatsResponse(totalCount, averagePrice, minPrice, maxPrice, categoryCount);
    }

    private static long toL(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static Double toD(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
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
