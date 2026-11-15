package com.eyup.kervan.order.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class OrderNotCancellableException extends ApiException {

    // an order that is already shipped or cancelled cannot be cancelled any more
    public OrderNotCancellableException() {
        super(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "Only pending orders can be cancelled");
    }
}
