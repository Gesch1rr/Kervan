package com.eyup.kervan.product.service;

import com.eyup.kervan.common.dto.PageResponse;
import com.eyup.kervan.common.exception.InvalidSortException;
import com.eyup.kervan.product.dto.CreateProductRequest;
import com.eyup.kervan.product.dto.ProductFilter;
import com.eyup.kervan.product.dto.ProductResponse;
import com.eyup.kervan.product.dto.UpdateProductRequest;
import com.eyup.kervan.product.entity.Category;
import com.eyup.kervan.product.entity.Product;
import com.eyup.kervan.product.exception.CategoryNotFoundException;
import com.eyup.kervan.product.exception.ProductAlreadyExistsException;
import com.eyup.kervan.product.exception.ProductNotFoundException;
import com.eyup.kervan.product.mapper.ProductMapper;
import com.eyup.kervan.product.repository.CategoryRepository;
import com.eyup.kervan.product.repository.ProductRepository;
import com.eyup.kervan.product.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

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

    public ProductResponse getActiveById(Long id) {
        Product product = findProduct(id);
        // soft-deleted products are invisible to the public API
        if (!product.isActive()) {
            throw new ProductNotFoundException(id);
        }
        return productMapper.toResponse(product);
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
    public void delete(Long id) {
        // soft delete; order history keeps pointing at the product
        findProduct(id).deactivate();
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
