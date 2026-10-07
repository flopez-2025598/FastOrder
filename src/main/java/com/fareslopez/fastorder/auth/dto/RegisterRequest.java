package com.fareslopez.fastorder.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de registro. El rol NO se recibe: siempre se asigna CLIENTE en el servidor.
 */
public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String nombre,

        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String direccion,

        @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
        String telefono,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        @JsonAlias("correo")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String password
) {
}
