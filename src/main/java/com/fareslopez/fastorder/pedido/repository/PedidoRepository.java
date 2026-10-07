package com.fareslopez.fastorder.pedido.repository;

import com.fareslopez.fastorder.pedido.entity.EstadoPedido;
import com.fareslopez.fastorder.pedido.entity.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Historial del cliente en sesión, del más reciente al más antiguo.
     * El EntityGraph trae detalles y productos en una sola consulta (evita N+1).
     */
    @EntityGraph(attributePaths = {"cliente", "repartidor", "detalles", "detalles.producto"})
    List<Pedido> findByClienteIdOrderByFechaPedidoDescIdDesc(Long clienteId);

    /**
     * Pedidos activos visibles para un REPARTIDOR: los que aún no tienen repartidor
     * asignado y los que ya tiene asignados él mismo.
     */
    @EntityGraph(attributePaths = {"cliente", "repartidor", "detalles", "detalles.producto"})
    @Query("""
            SELECT p FROM Pedido p
            WHERE p.estado IN :estados
              AND (p.repartidor IS NULL OR p.repartidor.id = :repartidorId)
            ORDER BY p.fechaPedido ASC, p.id ASC
            """)
    List<Pedido> findDisponiblesParaRepartidor(@Param("estados") Collection<EstadoPedido> estados,
                                               @Param("repartidorId") Long repartidorId);

    /**
     * Pedidos por estado (ADMIN: disponibles y auditoría).
     */
    @EntityGraph(attributePaths = {"cliente", "repartidor", "detalles", "detalles.producto"})
    List<Pedido> findByEstadoInOrderByFechaPedidoAscIdAsc(Collection<EstadoPedido> estados);

    /**
     * Todos los pedidos de la plataforma (ADMIN, auditoría).
     */
    @EntityGraph(attributePaths = {"cliente", "repartidor", "detalles", "detalles.producto"})
    List<Pedido> findAllByOrderByFechaPedidoDescIdDesc();

    @EntityGraph(attributePaths = {"cliente", "repartidor", "detalles", "detalles.producto"})
    Optional<Pedido> findWithDetallesById(Long id);

    /**
     * Bloqueo pesimista (SELECT ... FOR UPDATE) del pedido para que dos operaciones
     * simultáneas (p. ej. cancelar y pasar a EN_PREPARACION) no se pisen.
     * Sin joins: PostgreSQL no permite FOR UPDATE sobre el lado nulo de un LEFT JOIN.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdForUpdate(@Param("id") Long id);
}
