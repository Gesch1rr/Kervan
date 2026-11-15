package com.eyup.kervan.product.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CategoryInUseException extends ApiException {

    public CategoryInUseException(Long id) {
        super(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "Category " + id + " still has products");
    }
}
