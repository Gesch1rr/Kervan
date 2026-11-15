package com.eyup.kervan.product.service;

import com.eyup.kervan.common.dto.PageResponse;
import com.eyup.kervan.common.exception.InvalidSortException;
import com.eyup.kervan.product.dto.CreateProductRequest;
import com.eyup.kervan.product.dto.ProductFilter;
import com.eyup.kervan.product.dto.ProductResponse;
import com.eyup.kervan.product.dto.ProductSummary;
import com.eyup.kervan.product.dto.UpdateProductRequest;
import com.eyup.kervan.product.dto.UpdateStockRequest;
import com.eyup.kervan.product.entity.Category;
import com.eyup.kervan.product.entity.Product;
import com.eyup.kervan.product.exception.CategoryNotFoundException;
import com.eyup.kervan.product.exception.InsufficientStockException;
import com.eyup.kervan.product.exception.ProductAlreadyExistsException;
import com.eyup.kervan.product.exception.ProductNotFoundException;
import com.eyup.kervan.product.mapper.ProductMapper;
import com.eyup.kervan.product.repository.CategoryRepository;
import com.eyup.kervan.product.repository.ProductRepository;
import com.eyup.kervan.product.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    public static final String CACHE_NAME = "products";

    // whitelist: blocks sorting by unknown fields, which would otherwise be a 500
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "price", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public PageResponse<ProductResponse> list(ProductFilter filter, Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        }
        List<Specification<Product>> specs = new ArrayList<>();
        specs.add(ProductSpecifications.isActive());
        if (filter.categoryId() != null) {
            specs.add(ProductSpecifications.inCategory(filter.categoryId()));
        }
        if (StringUtils.hasText(filter.q())) {
            specs.add(ProductSpecifications.nameContains(filter.q().trim()));
        }
        return PageResponse.from(productRepository.findAll(Specification.allOf(specs), pageable).map(productMapper::toResponse));
    }

    // Only found, active products are cached: exceptions are never cached, so a 404 is not remembered.
    @Cacheable(cacheNames = CACHE_NAME, key = "#id")
    public ProductResponse getActiveById(Long id) {
        Product product = findProduct(id);
        // soft-deleted products are invisible to the public API
        if (!product.isActive()) {
            throw new ProductNotFoundException(id);
        }
        return productMapper.toResponse(product);
    }

    public ProductSummary getSummary(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toSummary)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // One query for many products (avoids N+1); missing ids are simply absent from the map.
    public Map<Long, ProductSummary> findSummaries(Collection<Long> ids) {
        return productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, productMapper::toSummary));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String name = request.name().trim();
        String slug = SlugGenerator.toSlug(name);
        if (productRepository.existsBySlug(slug)) {
            throw new ProductAlreadyExistsException();
        }
        Category category = findCategory(request.categoryId());
        Product product = productRepository.save(
                new Product(name, slug, request.description(), request.price(), request.stock(), category));
        return productMapper.toResponse(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#id")
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findProduct(id);
        // someone else changed the product after this client loaded it
        if (!product.getVersion().equals(request.version())) {
            throw new ObjectOptimisticLockingFailureException(Product.class, id);
        }
        String name = request.name().trim();
        String slug = SlugGenerator.toSlug(name);
        if (productRepository.existsBySlugAndIdNot(slug, id)) {
            throw new ProductAlreadyExistsException();
        }
        product.update(name, slug, request.description(), request.price(), findCategory(request.categoryId()));
        // flush now so the response carries the incremented version
        productRepository.flush();
        return productMapper.toResponse(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#id")
    public void delete(Long id) {
        // soft delete; order history keeps pointing at the product
        findProduct(id).deactivate();
    }

    // Joins the caller's transaction (checkout), so a later failure rolls the stock change back too.
    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#productId")
    public void decreaseStock(Long productId, int quantity) {
        requirePositive(quantity);
        if (productRepository.decreaseStock(productId, quantity, Instant.now()) == 0) {
            throw new InsufficientStockException(productId);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#productId")
    public void increaseStock(Long productId, int quantity) {
        requirePositive(quantity);
        if (productRepository.increaseStock(productId, quantity, Instant.now()) == 0) {
            throw new ProductNotFoundException(productId);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, key = "#id")
    public ProductResponse adjustStock(Long id, UpdateStockRequest request) {
        if (productRepository.adjustStock(id, request.delta(), Instant.now()) == 0) {
            if (!productRepository.existsById(id)) {
                throw new ProductNotFoundException(id);
            }
            throw new InsufficientStockException(id);
        }
        return productMapper.toResponse(findProduct(id));
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private Product findProduct(Long id) {
        return productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }
}
