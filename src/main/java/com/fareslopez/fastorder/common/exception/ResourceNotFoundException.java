package com.fareslopez.fastorder.common.exception;

/**
 * El recurso solicitado (comercio, producto, pedido, usuario) no existe. -> 404
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String recurso, Long id) {
        super(recurso + " con id " + id + " no encontrado");
    }
}
