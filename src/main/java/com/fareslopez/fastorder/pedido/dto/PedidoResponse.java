package com.fareslopez.fastorder.pedido.dto;

import com.fareslopez.fastorder.auth.entity.Usuario;
import com.fareslopez.fastorder.pedido.entity.EstadoPedido;
import com.fareslopez.fastorder.pedido.entity.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Long clienteId,
        String clienteNombre,
        Long repartidorId,
        String repartidorNombre,
        LocalDateTime fechaPedido,
        EstadoPedido estado,
        BigDecimal subtotalProductos,
        BigDecimal costoEnvio,
        BigDecimal montoTotal,
        List<DetallePedidoResponse> detalles
) {
    /**
     * Debe llamarse dentro de una transacción (las relaciones son LAZY y open-in-view está desactivado).
     */
    public static PedidoResponse from(Pedido p) {
        Usuario cliente = p.getCliente();
        Usuario repartidor = p.getRepartidor();
        return new PedidoResponse(
                p.getId(),
                cliente.getId(),
                cliente.getNombre(),
                repartidor != null ? repartidor.getId() : null,
                repartidor != null ? repartidor.getNombre() : null,
                p.getFechaPedido(),
                p.getEstado(),
                p.getMontoTotal().subtract(p.getCostoEnvio()),
                p.getCostoEnvio(),
                p.getMontoTotal(),
                p.getDetalles().stream().map(DetallePedidoResponse::from).toList());
    }
}
