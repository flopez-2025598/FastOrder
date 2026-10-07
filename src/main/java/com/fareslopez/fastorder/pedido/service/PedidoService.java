package com.fareslopez.fastorder.pedido.service;

import com.fareslopez.fastorder.auth.entity.Usuario;
import com.fareslopez.fastorder.auth.enums.Rol;
import com.fareslopez.fastorder.auth.repository.UsuarioRepository;
import com.fareslopez.fastorder.comercio.entity.Producto;
import com.fareslopez.fastorder.comercio.repository.ProductoRepository;
import com.fareslopez.fastorder.common.exception.BusinessException;
import com.fareslopez.fastorder.common.exception.InsufficientStockException;
import com.fareslopez.fastorder.common.exception.InvalidStatusException;
import com.fareslopez.fastorder.common.exception.ResourceNotFoundException;
import com.fareslopez.fastorder.pedido.dto.ItemPedidoRequest;
import com.fareslopez.fastorder.pedido.dto.PedidoRequest;
import com.fareslopez.fastorder.pedido.dto.PedidoResponse;
import com.fareslopez.fastorder.pedido.entity.DetallePedido;
import com.fareslopez.fastorder.pedido.entity.EstadoPedido;
import com.fareslopez.fastorder.pedido.entity.Pedido;
import com.fareslopez.fastorder.pedido.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    /** Estados en los que un pedido sigue "vivo" para el repartidor. */
    private static final Set<EstadoPedido> ESTADOS_ACTIVOS =
            EnumSet.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.EN_CAMINO);

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BigDecimal costoEnvio;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository,
                         @Value("${fastorder.costo-envio:20.00}") BigDecimal costoEnvio) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.costoEnvio = costoEnvio.setScale(2, RoundingMode.HALF_UP);
    }

    // =====================================================================
    // Crear pedido (CLIENTE)
    // =====================================================================

    /**
     * Crea el pedido en UNA sola transacción:
     * 1. Bloquea (FOR UPDATE) los productos solicitados, en orden de id.
     * 2. Valida existencia, disponibilidad, comercio abierto y stock.
     * 3. Descuenta stock y calcula subtotales con el precio ACTUAL del catálogo.
     * 4. Total = suma de subtotales + costo de envío fijo (Q20.00).
     * Si cualquier producto falla (p. ej. InsufficientStockException) se hace ROLLBACK
     * de todo: ningún stock queda descontado y el pedido no se guarda.
     */
    @Transactional(rollbackFor = Exception.class)
    public PedidoResponse crearPedido(String emailCliente, PedidoRequest request) {
        Usuario cliente = buscarUsuario(emailCliente);

        // Consolida cantidades si el mismo producto viene repetido en el carrito
        Map<Long, Integer> cantidadesPorProducto = new LinkedHashMap<>();
        for (ItemPedidoRequest item : request.items()) {
            cantidadesPorProducto.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        Map<Long, Producto> productos = productoRepository.findAllByIdForUpdate(cantidadesPorProducto.keySet())
                .stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setFechaPedido(LocalDateTime.now());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setCostoEnvio(costoEnvio);

        BigDecimal subtotalProductos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Map.Entry<Long, Integer> entry : cantidadesPorProducto.entrySet()) {
            Long productoId = entry.getKey();
            int cantidad = entry.getValue();

            Producto producto = productos.get(productoId);
            if (producto == null) {
                throw new ResourceNotFoundException("Producto", productoId);
            }
            if (!producto.isDisponible()) {
                throw new BusinessException("El producto '" + producto.getNombre() + "' no está disponible");
            }
            if (!producto.getComercio().isAbierto()) {
                throw new BusinessException("El comercio '" + producto.getComercio().getNombre()
                        + "' está cerrado y no acepta pedidos");
            }
            if (producto.getStock() < cantidad) {
                throw new InsufficientStockException(producto.getId(), producto.getNombre(),
                        producto.getStock(), cantidad);
            }

            // Descuento de stock (se persiste al hacer commit, solo si TODO el pedido es válido)
            producto.setStock(producto.getStock() - cantidad);

            BigDecimal precioActual = producto.getPrecio().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = precioActual.multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            DetallePedido detalle = new DetallePedido();
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(precioActual);
            detalle.setSubtotal(subtotal);
            pedido.agregarDetalle(detalle);

            subtotalProductos = subtotalProductos.add(subtotal);
        }

        pedido.setMontoTotal(subtotalProductos.add(costoEnvio));
        return PedidoResponse.from(pedidoRepository.save(pedido));
    }

    // =====================================================================
    // Consultas
    // =====================================================================

    @Transactional(readOnly = true)
    public List<PedidoResponse> misPedidos(String emailCliente) {
        Usuario cliente = buscarUsuario(emailCliente);
        return pedidoRepository.findByClienteIdOrderByFechaPedidoDescIdDesc(cliente.getId())
                .stream().map(PedidoResponse::from).toList();
    }

    /**
     * REPARTIDOR: pedidos activos sin repartidor + los que él tiene asignados.
     * ADMIN: todos los pedidos activos (PENDIENTE, EN_PREPARACION, EN_CAMINO).
     */
    @Transactional(readOnly = true)
    public List<PedidoResponse> disponibles(String email) {
        Usuario usuario = buscarUsuario(email);
        List<Pedido> pedidos = (usuario.getRol() == Rol.ADMIN)
                ? pedidoRepository.findByEstadoInOrderByFechaPedidoAscIdAsc(ESTADOS_ACTIVOS)
                : pedidoRepository.findDisponiblesParaRepartidor(ESTADOS_ACTIVOS, usuario.getId());
        return pedidos.stream().map(PedidoResponse::from).toList();
    }

    /**
     * ADMIN (auditoría): todos los pedidos, con filtro opcional por estado.
     */
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarTodos(EstadoPedido estado) {
        List<Pedido> pedidos = (estado == null)
                ? pedidoRepository.findAllByOrderByFechaPedidoDescIdDesc()
                : pedidoRepository.findByEstadoInOrderByFechaPedidoAscIdAsc(EnumSet.of(estado));
        return pedidos.stream().map(PedidoResponse::from).toList();
    }

    /**
     * Seguimiento de un pedido: el CLIENTE solo ve los suyos; el REPARTIDOR los que tiene
     * asignados o los que aún no tienen repartidor; el ADMIN ve todos.
     */
    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long pedidoId, String email) {
        Usuario usuario = buscarUsuario(email);
        Pedido pedido = pedidoRepository.findWithDetallesById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        switch (usuario.getRol()) {
            case CLIENTE -> {
                if (!esMismoUsuario(pedido.getCliente(), usuario)) {
                    throw new AccessDeniedException("Solo puede consultar sus propios pedidos");
                }
            }
            case REPARTIDOR -> {
                if (pedido.getRepartidor() != null && !esMismoUsuario(pedido.getRepartidor(), usuario)) {
                    throw new AccessDeniedException("El pedido está asignado a otro repartidor");
                }
            }
            case ADMIN -> { /* acceso total */ }
        }
        return PedidoResponse.from(pedido);
    }

    // =====================================================================
    // Cambio de estado (REPARTIDOR / ADMIN)
    // =====================================================================

    /**
     * Avanza el pedido exactamente un paso: PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO.
     * El primer REPARTIDOR que actualiza un pedido sin asignar queda como su repartidor.
     */
    @Transactional(rollbackFor = Exception.class)
    public PedidoResponse actualizarEstado(Long pedidoId, EstadoPedido nuevoEstado, String email) {
        Usuario actor = buscarUsuario(email);
        Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (nuevoEstado == EstadoPedido.CANCELADO) {
            throw new InvalidStatusException(
                    "Para cancelar un pedido use PATCH /api/v1/pedidos/" + pedidoId + "/cancelar");
        }
        if (nuevoEstado == EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException("Un pedido no puede regresar al estado PENDIENTE");
        }

        EstadoPedido actual = pedido.getEstado();
        if (actual.esFinal()) {
            throw new InvalidStatusException("El pedido " + pedidoId + " ya está en estado final " + actual
                    + " y no puede modificarse");
        }
        if (!actual.puedeAvanzarA(nuevoEstado)) {
            throw new InvalidStatusException("Transición inválida: " + actual + " -> " + nuevoEstado
                    + ". El siguiente estado permitido es " + actual.siguiente());
        }

        if (actor.getRol() == Rol.REPARTIDOR) {
            if (pedido.getRepartidor() == null) {
                pedido.setRepartidor(actor); // el repartidor toma el pedido
            } else if (!esMismoUsuario(pedido.getRepartidor(), actor)) {
                throw new AccessDeniedException("El pedido " + pedidoId + " ya está asignado a otro repartidor");
            }
        }

        pedido.setEstado(nuevoEstado);
        return PedidoResponse.from(pedido);
    }

    // =====================================================================
    // Cancelación (CLIENTE dueño / ADMIN)
    // =====================================================================

    /**
     * Cancela el pedido (solo si está PENDIENTE) y devuelve al inventario el stock de cada producto.
     */
    @Transactional(rollbackFor = Exception.class)
    public PedidoResponse cancelar(Long pedidoId, String email) {
        Usuario actor = buscarUsuario(email);
        Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (actor.getRol() == Rol.CLIENTE && !esMismoUsuario(pedido.getCliente(), actor)) {
            throw new AccessDeniedException("Solo puede cancelar sus propios pedidos");
        }
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException("Solo se puede cancelar un pedido en estado PENDIENTE (estado actual: "
                    + pedido.getEstado() + ")");
        }

        // Restaurar stock bloqueando los productos en el mismo orden que al crear pedidos (sin deadlocks)
        Map<Long, Integer> devolucion = new LinkedHashMap<>();
        for (DetallePedido d : pedido.getDetalles()) {
            devolucion.merge(d.getProducto().getId(), d.getCantidad(), Integer::sum);
        }
        if (!devolucion.isEmpty()) {
            for (Producto producto : productoRepository.findAllByIdForUpdate(devolucion.keySet())) {
                producto.setStock(producto.getStock() + devolucion.get(producto.getId()));
            }
        }

        pedido.setEstado(EstadoPedido.CANCELADO);
        return PedidoResponse.from(pedido);
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con email " + email + " no encontrado"));
    }

    private boolean esMismoUsuario(Usuario a, Usuario b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }
}
