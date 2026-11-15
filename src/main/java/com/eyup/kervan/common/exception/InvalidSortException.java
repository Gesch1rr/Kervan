package com.eyup.kervan.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidSortException extends ApiException {

    public InvalidSortException(String property) {
        super(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Sorting by '" + property + "' is not supported");
    }
}
