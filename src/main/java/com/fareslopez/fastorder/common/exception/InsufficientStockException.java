package com.fareslopez.fastorder.common.exception;

import lombok.Getter;

/**
 * Stock insuficiente para un producto del pedido. Al ser RuntimeException,
 * provoca el ROLLBACK completo de la transacción del pedido. -> 409
 */
@Getter
public class InsufficientStockException extends RuntimeException {

    private final Long productoId;
    private final int stockDisponible;
    private final int cantidadSolicitada;

    public InsufficientStockException(Long productoId, String nombreProducto, int stockDisponible, int cantidadSolicitada) {
        super("Stock insuficiente para '" + nombreProducto + "' (id " + productoId + "): disponible "
                + stockDisponible + ", solicitado " + cantidadSolicitada);
        this.productoId = productoId;
        this.stockDisponible = stockDisponible;
        this.cantidadSolicitada = cantidadSolicitada;
    }
}
