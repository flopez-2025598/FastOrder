package com.fareslopez.fastorder.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        @JsonAlias("correo")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}
