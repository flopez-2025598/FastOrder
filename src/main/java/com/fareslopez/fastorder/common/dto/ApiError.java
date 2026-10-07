package com.fareslopez.fastorder.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato único de error que devuelve toda la API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> detalles
) {
    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ApiError of(HttpStatus status, String message, String path, Map<String, String> detalles) {
        return new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path, detalles);
    }
}
