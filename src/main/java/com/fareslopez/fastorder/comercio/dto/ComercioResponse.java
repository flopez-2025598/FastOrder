package com.fareslopez.fastorder.comercio.dto;

import com.fareslopez.fastorder.comercio.entity.Categoria;
import com.fareslopez.fastorder.comercio.entity.Comercio;

public record ComercioResponse(
        Long id,
        String nombre,
        Categoria categoria,
        String direccion,
        boolean abierto
) {
    public static ComercioResponse from(Comercio c) {
        return new ComercioResponse(c.getId(), c.getNombre(), c.getCategoria(), c.getDireccion(), c.isAbierto());
    }
}
