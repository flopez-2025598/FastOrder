package com.fareslopez.fastorder.comercio.repository;

import com.fareslopez.fastorder.comercio.entity.Categoria;
import com.fareslopez.fastorder.comercio.entity.Comercio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComercioRepository extends JpaRepository<Comercio, Long> {

    List<Comercio> findByAbiertoTrueOrderByNombreAsc();

    List<Comercio> findByAbiertoTrueAndCategoriaOrderByNombreAsc(Categoria categoria);
}
