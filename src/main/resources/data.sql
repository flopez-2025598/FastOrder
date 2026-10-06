-- Insertar 1 ADMIN, 1 REPARTIDOR y 1 CLIENTE con la contraseña "password123" encriptada en BCrypt
INSERT INTO usuarios (nombre, correo, password_hash, rol) VALUES
('Admin Principal', 'admin@fastorder.com', '$2a$10$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'ADMIN'),
('Repartidor Juan', 'repartidor@fastorder.com', '$2a$10$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'REPARTIDOR'),
('Cliente Fares', 'cliente@fastorder.com', '$2a$10$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'CLIENTE')
ON CONFLICT (correo) DO NOTHING;

-- Insertar 1 COMERCIO
INSERT INTO comercios (nombre, categoria, direccion, abierto) VALUES
('Burger King', 'RESTAURANTE', 'Zona 10, Ciudad', true)
ON CONFLICT DO NOTHING;