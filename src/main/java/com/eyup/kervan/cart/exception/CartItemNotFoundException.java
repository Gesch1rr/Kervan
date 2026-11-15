package com.eyup.kervan.cart.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CartItemNotFoundException extends ApiException {

    public CartItemNotFoundException(Long productId) {
        super(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Product " + productId + " is not in the cart");
    }
}
