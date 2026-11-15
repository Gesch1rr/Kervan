package com.eyup.kervan.product.mapper;

import com.eyup.kervan.product.dto.CategoryResponse;
import com.eyup.kervan.product.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
