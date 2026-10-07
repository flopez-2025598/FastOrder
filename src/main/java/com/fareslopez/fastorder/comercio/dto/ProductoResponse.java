package com.fareslopez.fastorder.comercio.dto;

import com.fareslopez.fastorder.comercio.entity.Producto;

import java.math.BigDecimal;

public record ProductoResponse(
        Long id,
        Long comercioId,
        String nombre,
        BigDecimal precio,
        Integer stock,
        boolean disponible
) {
    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(p.getId(), p.getComercio().getId(), p.getNombre(),
                p.getPrecio(), p.getStock(), p.isDisponible());
    }
}
