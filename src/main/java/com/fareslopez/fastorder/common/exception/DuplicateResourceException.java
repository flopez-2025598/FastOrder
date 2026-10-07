package com.fareslopez.fastorder.common.exception;

/**
 * Se intenta crear un recurso que ya existe (por ejemplo, email repetido). -> 409
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
