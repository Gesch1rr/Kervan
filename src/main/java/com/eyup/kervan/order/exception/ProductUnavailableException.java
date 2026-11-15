package com.eyup.kervan.order.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProductUnavailableException extends ApiException {

    public ProductUnavailableException(Long productId) {
        super(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product " + productId + " is no longer available");
    }
}
