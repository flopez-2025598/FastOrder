package com.fareslopez.fastorder.common.exception;

/**
 * Violación de una regla de negocio general (comercio cerrado, producto no disponible, etc.). -> 400
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
