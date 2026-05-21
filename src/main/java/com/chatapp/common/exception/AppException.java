package com.chatapp.common.exception;

import lombok.Getter;

/**
 * Application-level runtime exception carrying a structured {@link ErrorCode}.
 * Caught by {@link GlobalExceptionHandler} and mapped to the appropriate HTTP
 * status and message.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
