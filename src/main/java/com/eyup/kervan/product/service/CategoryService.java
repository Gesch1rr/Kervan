package com.eyup.kervan.product.service;

import com.eyup.kervan.product.dto.CategoryResponse;
import com.eyup.kervan.product.dto.CreateCategoryRequest;
import com.eyup.kervan.product.dto.UpdateCategoryRequest;
import com.eyup.kervan.product.entity.Category;
import com.eyup.kervan.product.exception.CategoryAlreadyExistsException;
import com.eyup.kervan.product.exception.CategoryNotFoundException;
import com.eyup.kervan.product.mapper.CategoryMapper;
import com.eyup.kervan.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    public static final String CACHE_NAME = "categories";

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Cacheable(cacheNames = CACHE_NAME, key = "'all'")
    public List<CategoryResponse> list() {
        // a plain ArrayList, because JDK-internal immutable lists (stream().toList()) cannot be rebuilt from the cached JSON
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        String slug = SlugGenerator.toSlug(name);
        if (categoryRepository.existsBySlug(slug)) {
            throw new CategoryAlreadyExistsException();
        }
        return categoryMapper.toResponse(categoryRepository.save(new Category(name, slug)));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();
        String slug = SlugGenerator.toSlug(name);
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new CategoryAlreadyExistsException();
        }
        category.rename(name, slug);
        return categoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new CategoryNotFoundException(id);
        }
        categoryRepository.deleteById(id);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }
}
