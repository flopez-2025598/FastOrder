package com.fareslopez.fastorder.comercio.dto;

import com.fareslopez.fastorder.comercio.entity.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComercioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String nombre,

        @NotNull(message = "La categoría es obligatoria (RESTAURANTE, SUPERMERCADO, FARMACIA)")
        Categoria categoria,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String direccion,

        // Opcional: si no se envía, el comercio se registra abierto
        Boolean abierto
) {
}
