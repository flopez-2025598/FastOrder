package com.fareslopez.fastorder.pedido.entity;

/**
 * Flujo estricto del pedido:
 * PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO
 * CANCELADO solo es alcanzable desde PENDIENTE (endpoint /cancelar).
 */
public enum EstadoPedido {
    PENDIENTE,
    EN_PREPARACION,
    EN_CAMINO,
    ENTREGADO,
    CANCELADO;

    /**
     * Siguiente estado de la secuencia normal, o null si el estado es final.
     */
    public EstadoPedido siguiente() {
        return switch (this) {
            case PENDIENTE -> EN_PREPARACION;
            case EN_PREPARACION -> EN_CAMINO;
            case EN_CAMINO -> ENTREGADO;
            case ENTREGADO, CANCELADO -> null;
        };
    }

    /**
     * Solo se permite avanzar exactamente un paso en la secuencia (no se puede saltar ni retroceder).
     */
    public boolean puedeAvanzarA(EstadoPedido nuevo) {
        return nuevo != null && nuevo == siguiente();
    }

    public boolean esFinal() {
        return this == ENTREGADO || this == CANCELADO;
    }
}
