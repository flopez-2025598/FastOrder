package com.fareslopez.fastorder.comercio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comercios")
@Getter
@Setter
@NoArgsConstructor
public class Comercio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Column(nullable = false)
    private boolean abierto = true;

    // Catálogo del comercio (lado inverso de Producto.comercio)
    @OneToMany(mappedBy = "comercio", fetch = FetchType.LAZY)
    private List<Producto> productos = new ArrayList<>();
}
