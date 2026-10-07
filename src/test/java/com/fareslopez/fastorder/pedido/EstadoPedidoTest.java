package com.fareslopez.fastorder.pedido;

import com.fareslopez.fastorder.pedido.entity.EstadoPedido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoPedidoTest {

    @Test
    void soloSePermiteAvanzarUnPaso() {
        assertThat(EstadoPedido.PENDIENTE.puedeAvanzarA(EstadoPedido.EN_PREPARACION)).isTrue();
        assertThat(EstadoPedido.EN_PREPARACION.puedeAvanzarA(EstadoPedido.EN_CAMINO)).isTrue();
        assertThat(EstadoPedido.EN_CAMINO.puedeAvanzarA(EstadoPedido.ENTREGADO)).isTrue();

        assertThat(EstadoPedido.PENDIENTE.puedeAvanzarA(EstadoPedido.EN_CAMINO)).isFalse();
        assertThat(EstadoPedido.PENDIENTE.puedeAvanzarA(EstadoPedido.ENTREGADO)).isFalse();
        assertThat(EstadoPedido.EN_CAMINO.puedeAvanzarA(EstadoPedido.EN_PREPARACION)).isFalse();
        assertThat(EstadoPedido.ENTREGADO.puedeAvanzarA(EstadoPedido.EN_CAMINO)).isFalse();
        assertThat(EstadoPedido.CANCELADO.puedeAvanzarA(EstadoPedido.EN_PREPARACION)).isFalse();
    }

    @Test
    void estadosFinales() {
        assertThat(EstadoPedido.ENTREGADO.esFinal()).isTrue();
        assertThat(EstadoPedido.CANCELADO.esFinal()).isTrue();
        assertThat(EstadoPedido.PENDIENTE.esFinal()).isFalse();
    }
}
