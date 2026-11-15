package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {

    public CategoryNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category " + id + " not found");
    }
}
