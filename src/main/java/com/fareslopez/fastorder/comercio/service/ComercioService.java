package com.fareslopez.fastorder.comercio.service;

import com.fareslopez.fastorder.comercio.dto.ComercioRequest;
import com.fareslopez.fastorder.comercio.dto.ComercioResponse;
import com.fareslopez.fastorder.comercio.dto.ProductoRequest;
import com.fareslopez.fastorder.comercio.dto.ProductoResponse;
import com.fareslopez.fastorder.comercio.entity.Categoria;
import com.fareslopez.fastorder.comercio.entity.Comercio;
import com.fareslopez.fastorder.comercio.entity.Producto;
import com.fareslopez.fastorder.comercio.repository.ComercioRepository;
import com.fareslopez.fastorder.comercio.repository.ProductoRepository;
import com.fareslopez.fastorder.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComercioService {

    private final ComercioRepository comercioRepository;
    private final ProductoRepository productoRepository;

    /**
     * Lista los comercios activos (abiertos), con filtro opcional por categoría.
     */
    @Transactional(readOnly = true)
    public List<ComercioResponse> listarActivos(Categoria categoria) {
        List<Comercio> comercios = (categoria == null)
                ? comercioRepository.findByAbiertoTrueOrderByNombreAsc()
                : comercioRepository.findByAbiertoTrueAndCategoriaOrderByNombreAsc(categoria);
        return comercios.stream().map(ComercioResponse::from).toList();
    }

    @Transactional
    public ComercioResponse crear(ComercioRequest request) {
        Comercio comercio = new Comercio();
        comercio.setNombre(request.nombre().trim());
        comercio.setCategoria(request.categoria());
        comercio.setDireccion(request.direccion().trim());
        comercio.setAbierto(request.abierto() == null || request.abierto());
        return ComercioResponse.from(comercioRepository.save(comercio));
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarProductos(Long comercioId) {
        if (!comercioRepository.existsById(comercioId)) {
            throw new ResourceNotFoundException("Comercio", comercioId);
        }
        return productoRepository.findByComercioIdOrderByIdAsc(comercioId).stream()
                .map(ProductoResponse::from)
                .toList();
    }

    @Transactional
    public ProductoResponse agregarProducto(Long comercioId, ProductoRequest request) {
        Comercio comercio = comercioRepository.findById(comercioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio", comercioId));

        Producto producto = new Producto();
        producto.setComercio(comercio);
        producto.setNombre(request.nombre().trim());
        producto.setPrecio(request.precio().setScale(2, RoundingMode.HALF_UP));
        producto.setStock(request.stock());
        producto.setDisponible(request.disponible() == null || request.disponible());
        return ProductoResponse.from(productoRepository.save(producto));
    }
}
