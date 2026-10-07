package com.fareslopez.fastorder.comercio.controller;

import com.fareslopez.fastorder.comercio.dto.ComercioRequest;
import com.fareslopez.fastorder.comercio.dto.ComercioResponse;
import com.fareslopez.fastorder.comercio.dto.ProductoRequest;
import com.fareslopez.fastorder.comercio.dto.ProductoResponse;
import com.fareslopez.fastorder.comercio.entity.Categoria;
import com.fareslopez.fastorder.comercio.service.ComercioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comercios")
@RequiredArgsConstructor
public class ComercioController {

    private final ComercioService comercioService;

    // GET /api/v1/comercios?categoria=RESTAURANTE  (Autenticado)
    @GetMapping
    public ResponseEntity<List<ComercioResponse>> listar(@RequestParam(required = false) Categoria categoria) {
        return ResponseEntity.ok(comercioService.listarActivos(categoria));
    }

    // POST /api/v1/comercios  (ADMIN)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComercioResponse> crear(@Valid @RequestBody ComercioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comercioService.crear(request));
    }

    // GET /api/v1/comercios/{id}/productos  (Autenticado)
    @GetMapping("/{id}/productos")
    public ResponseEntity<List<ProductoResponse>> listarProductos(@PathVariable Long id) {
        return ResponseEntity.ok(comercioService.listarProductos(id));
    }

    // POST /api/v1/comercios/{id}/productos  (ADMIN)
    @PostMapping("/{id}/productos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoResponse> agregarProducto(@PathVariable Long id,
                                                            @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comercioService.agregarProducto(id, request));
    }
}
