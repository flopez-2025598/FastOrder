package com.fareslopez.fastorder.pedido.dto;

import com.fareslopez.fastorder.pedido.entity.EstadoPedido;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PATCH /api/v1/pedidos/{id}/estado. Ej: {"estado": "EN_PREPARACION"}
 */
public record ActualizarEstadoRequest(
        @NotNull(message = "El estado es obligatorio (EN_PREPARACION, EN_CAMINO, ENTREGADO)")
        @JsonAlias("nuevoEstado")
        EstadoPedido estado
) {
}
