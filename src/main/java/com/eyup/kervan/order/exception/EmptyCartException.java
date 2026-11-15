package com.eyup.kervan.order.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class EmptyCartException extends ApiException {

    public EmptyCartException() {
        super(HttpStatus.BAD_REQUEST, "EMPTY_CART", "The cart is empty");
    }
}
