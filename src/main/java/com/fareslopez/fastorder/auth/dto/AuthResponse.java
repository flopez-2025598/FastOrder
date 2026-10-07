package com.fareslopez.fastorder.auth.dto;

import com.fareslopez.fastorder.auth.enums.Rol;

public record AuthResponse(
        String token,
        String tipo,
        Long id,
        String nombre,
        String email,
        Rol rol
) {
    public static AuthResponse bearer(String token, Long id, String nombre, String email, Rol rol) {
        return new AuthResponse(token, "Bearer", id, nombre, email, rol);
    }
}
