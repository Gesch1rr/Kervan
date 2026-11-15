package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CategoryAlreadyExistsException extends ApiException {

    public CategoryAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "CATEGORY_ALREADY_EXISTS", "A category with this name already exists");
    }
}
