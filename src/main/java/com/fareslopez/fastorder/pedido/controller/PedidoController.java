package com.fareslopez.fastorder.pedido.controller;

import com.fareslopez.fastorder.pedido.dto.ActualizarEstadoRequest;
import com.fareslopez.fastorder.pedido.dto.PedidoRequest;
import com.fareslopez.fastorder.pedido.dto.PedidoResponse;
import com.fareslopez.fastorder.pedido.entity.EstadoPedido;
import com.fareslopez.fastorder.pedido.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    // POST /api/v1/pedidos  (CLIENTE) - valida stock, calcula totales y descuenta inventario
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoRequest request,
                                                Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.crearPedido(authentication.getName(), request));
    }

    // GET /api/v1/pedidos/mis-pedidos  (CLIENTE)
    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<List<PedidoResponse>> misPedidos(Authentication authentication) {
        return ResponseEntity.ok(pedidoService.misPedidos(authentication.getName()));
    }

    // GET /api/v1/pedidos/disponibles  (REPARTIDOR, ADMIN)
    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('REPARTIDOR', 'ADMIN')")
    public ResponseEntity<List<PedidoResponse>> disponibles(Authentication authentication) {
        return ResponseEntity.ok(pedidoService.disponibles(authentication.getName()));
    }

    // PATCH /api/v1/pedidos/{id}/estado  (REPARTIDOR, ADMIN)  body: {"estado":"EN_PREPARACION"}
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('REPARTIDOR', 'ADMIN')")
    public ResponseEntity<PedidoResponse> actualizarEstado(@PathVariable Long id,
                                                           @Valid @RequestBody ActualizarEstadoRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(pedidoService.actualizarEstado(id, request.estado(), authentication.getName()));
    }

    // PATCH /api/v1/pedidos/{id}/cancelar  (CLIENTE, ADMIN) - solo si está PENDIENTE; restaura stock
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN')")
    public ResponseEntity<PedidoResponse> cancelar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(pedidoService.cancelar(id, authentication.getName()));
    }

    // ---------- Extras: seguimiento y auditoría ----------

    // GET /api/v1/pedidos?estado=ENTREGADO  (ADMIN) - auditoría de la plataforma
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PedidoResponse>> listarTodos(@RequestParam(required = false) EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.listarTodos(estado));
    }

    // GET /api/v1/pedidos/{id}  (Autenticado) - seguimiento; el CLIENTE solo ve los suyos
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtener(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(pedidoService.obtener(id, authentication.getName()));
    }
}
