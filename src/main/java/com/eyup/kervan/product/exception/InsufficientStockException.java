package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(Long productId) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough stock for product " + productId);
    }
}
