package com.eyup.kervan.product.mapper;

import com.eyup.kervan.product.dto.ProductResponse;
import com.eyup.kervan.product.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public interface ProductMapper {

    ProductResponse toResponse(Product product);
}
