package com.eyup.kervan.order.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends ApiException {

    // same error for "does not exist" and "not yours", so order ids are never confirmed to other users
    public OrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
    }
}
