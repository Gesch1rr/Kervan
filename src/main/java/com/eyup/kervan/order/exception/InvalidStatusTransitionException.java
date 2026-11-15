package com.eyup.kervan.order.exception;

import com.eyup.kervan.common.exception.ApiException;
import com.eyup.kervan.order.entity.OrderStatus;
import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    // an order can only move forward along the allowed transitions
    public InvalidStatusTransitionException(OrderStatus target) {
        super(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Order cannot be moved to " + target);
    }
}
