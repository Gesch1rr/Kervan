package com.eyup.kervan.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// The items come from the user's cart; the client only says where to ship.
public record CreateOrderRequest(@NotNull @Valid ShippingAddress shippingAddress) {
}
