package com.fareslopez.fastorder.comercio.repository;

import com.fareslopez.fastorder.comercio.entity.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByComercioIdOrderByIdAsc(Long comercioId);

    /**
     * Bloqueo pesimista (SELECT ... FOR UPDATE) de los productos del pedido.
     * Se ordenan por id para que transacciones concurrentes bloqueen siempre en el
     * mismo orden y no se produzcan deadlocks. Evita vender más stock del que existe.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id IN :ids ORDER BY p.id")
    List<Producto> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
