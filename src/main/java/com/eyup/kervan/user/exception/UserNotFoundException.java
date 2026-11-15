package com.eyup.kervan.user.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + id + " not found");
    }
}
