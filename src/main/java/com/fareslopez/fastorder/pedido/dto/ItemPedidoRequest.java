package com.fareslopez.fastorder.pedido.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Un ítem del carrito: solo producto y cantidad.
 * El precio NO se recibe; se toma del catálogo en el servidor.
 */
public record ItemPedidoRequest(
        @NotNull(message = "El productoId es obligatorio")
        @Positive(message = "El productoId debe ser positivo")
        @JsonAlias({"producto_id", "idProducto", "id"})
        Long productoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 1000, message = "La cantidad máxima por producto es 1000")
        Integer cantidad
) {
}
