package com.fareslopez.fastorder.common.exception;

/**
 * Transición de estado de pedido no permitida por el flujo
 * PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO (o CANCELADO). -> 400
 */
public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String message) {
        super(message);
    }
}
