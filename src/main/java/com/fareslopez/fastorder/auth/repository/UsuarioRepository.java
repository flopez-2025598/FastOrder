package com.fareslopez.fastorder.auth.repository;

import com.fareslopez.fastorder.auth.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Más adelante agregaremos métodos personalizados aquí
}