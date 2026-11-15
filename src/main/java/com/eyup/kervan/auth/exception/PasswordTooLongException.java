package com.eyup.kervan.auth.exception;

import com.eyup.kervan.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PasswordTooLongException extends ApiException {

    public PasswordTooLongException() {
        super(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Password must be at most 72 bytes");
    }
}
