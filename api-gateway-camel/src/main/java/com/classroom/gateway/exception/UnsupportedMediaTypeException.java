package com.classroom.gateway.exception;

/**
 * Thrown when the request Content-Type is not {@code application/json}.
 * Camel's {@code onException} handler maps this to HTTP 415 Unsupported Media Type.
 */
public class UnsupportedMediaTypeException extends RuntimeException {
    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}

