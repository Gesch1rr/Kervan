package com.eyup.kervan.order.mapper;

import com.eyup.kervan.order.dto.OrderResponse;
import com.eyup.kervan.order.dto.OrderSummaryResponse;
import com.eyup.kervan.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    OrderSummaryResponse toSummary(Order order);
}
