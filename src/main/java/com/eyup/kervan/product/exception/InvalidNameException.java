package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidNameException extends ApiException {

    public InvalidNameException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Name must contain at least one latin letter or digit");
    }
}
