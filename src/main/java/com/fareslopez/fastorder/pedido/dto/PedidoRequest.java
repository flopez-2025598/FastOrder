package com.fareslopez.fastorder.pedido.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cuerpo de POST /api/v1/pedidos.
 * Cualquier total, subtotal o precio que envíe el cliente se ignora: el servidor recalcula todo.
 */
public record PedidoRequest(
        @NotEmpty(message = "El pedido debe tener al menos un producto")
        @Size(max = 50, message = "El pedido admite máximo 50 ítems")
        @JsonAlias({"detalles", "productos"})
        List<@Valid @NotNull(message = "El ítem no puede ser nulo") ItemPedidoRequest> items
) {
}
