package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends ApiException {

    public ProductNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product " + id + " not found");
    }
}
