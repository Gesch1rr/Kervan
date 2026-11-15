package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProductAlreadyExistsException extends ApiException {

    public ProductAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "PRODUCT_ALREADY_EXISTS", "A product with this name already exists");
    }
}
