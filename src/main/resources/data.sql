-- =====================================================================
-- Datos iniciales FastOrder (idempotente: se puede ejecutar en cada arranque)
-- Credenciales de los usuarios semilla (guardadas con BCrypt):
--   admin@fastorder.com       / Admin123*
--   repartidor@fastorder.com  / Repartidor123*
--   cliente.demo@fastorder.com / Cliente123*
-- (cliente@fastorder.com NO se precarga: el script test-api4.sh lo registra en el paso [1])
-- =====================================================================

-- Usuarios: 1 ADMIN, 1 REPARTIDOR, 1 CLIENTE
INSERT INTO usuarios (nombre, direccion, telefono, email, password, rol)
SELECT 'Admin Principal', 'Zona 1, Ciudad de Guatemala', '5555-0001', 'admin@fastorder.com',
       '$2a$10$U/ZzFERYAXDfzJKjbDlmOeMN0GoD5Y46.zSWI9RqbQx3sIdLzkLXm', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE email = 'admin@fastorder.com');

INSERT INTO usuarios (nombre, direccion, telefono, email, password, rol)
SELECT 'Repartidor Juan', 'Zona 7, Ciudad de Guatemala', '5555-0002', 'repartidor@fastorder.com',
       '$2a$10$4W21LzShxasVhO9UFblFAuagsmafZSQxmOmvWF7Idor6ue4JkPdFK', 'REPARTIDOR'
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE email = 'repartidor@fastorder.com');

INSERT INTO usuarios (nombre, direccion, telefono, email, password, rol)
SELECT 'Cliente Demo', 'Zona 10, Ciudad de Guatemala', '5555-0003', 'cliente.demo@fastorder.com',
       '$2a$10$PTasKTkDR1yWA3k5/jyNS.1OWDiKU0gg.cnScbdFain0djv9xGGam', 'CLIENTE'
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE email = 'cliente.demo@fastorder.com');

-- Si la base ya existía con las contraseñas anteriores (password123), se sincronizan.
-- La línea de cliente@fastorder.com solo afecta bases creadas con la versión anterior del data.sql.
UPDATE usuarios SET password = '$2a$10$U/ZzFERYAXDfzJKjbDlmOeMN0GoD5Y46.zSWI9RqbQx3sIdLzkLXm', rol = 'ADMIN' WHERE email = 'admin@fastorder.com';
UPDATE usuarios SET password = '$2a$10$4W21LzShxasVhO9UFblFAuagsmafZSQxmOmvWF7Idor6ue4JkPdFK', rol = 'REPARTIDOR' WHERE email = 'repartidor@fastorder.com';
UPDATE usuarios SET password = '$2a$10$PTasKTkDR1yWA3k5/jyNS.1OWDiKU0gg.cnScbdFain0djv9xGGam', rol = 'CLIENTE' WHERE email IN ('cliente@fastorder.com', 'cliente.demo@fastorder.com');

-- Comercios
INSERT INTO comercios (nombre, categoria, direccion, abierto)
SELECT 'Burger King', 'RESTAURANTE', 'Zona 10, Ciudad de Guatemala', true
WHERE NOT EXISTS (SELECT 1 FROM comercios WHERE nombre = 'Burger King');

INSERT INTO comercios (nombre, categoria, direccion, abierto)
SELECT 'Super Express', 'SUPERMERCADO', 'Zona 9, Ciudad de Guatemala', true
WHERE NOT EXISTS (SELECT 1 FROM comercios WHERE nombre = 'Super Express');

INSERT INTO comercios (nombre, categoria, direccion, abierto)
SELECT 'Farmacia Salud', 'FARMACIA', 'Zona 4, Ciudad de Guatemala', true
WHERE NOT EXISTS (SELECT 1 FROM comercios WHERE nombre = 'Farmacia Salud');

-- Productos
INSERT INTO productos (comercio_id, nombre, precio, stock, disponible)
SELECT c.id, 'Whopper', 45.00, 100, true FROM comercios c
WHERE c.nombre = 'Burger King'
  AND NOT EXISTS (SELECT 1 FROM productos p WHERE p.nombre = 'Whopper' AND p.comercio_id = c.id);

INSERT INTO productos (comercio_id, nombre, precio, stock, disponible)
SELECT c.id, 'Papas Grandes', 18.50, 100, true FROM comercios c
WHERE c.nombre = 'Burger King'
  AND NOT EXISTS (SELECT 1 FROM productos p WHERE p.nombre = 'Papas Grandes' AND p.comercio_id = c.id);

INSERT INTO productos (comercio_id, nombre, precio, stock, disponible)
SELECT c.id, 'Coca-Cola 600ml', 12.00, 100, true FROM comercios c
WHERE c.nombre = 'Burger King'
  AND NOT EXISTS (SELECT 1 FROM productos p WHERE p.nombre = 'Coca-Cola 600ml' AND p.comercio_id = c.id);

INSERT INTO productos (comercio_id, nombre, precio, stock, disponible)
SELECT c.id, 'Leche 1L', 14.75, 50, true FROM comercios c
WHERE c.nombre = 'Super Express'
  AND NOT EXISTS (SELECT 1 FROM productos p WHERE p.nombre = 'Leche 1L' AND p.comercio_id = c.id);

INSERT INTO productos (comercio_id, nombre, precio, stock, disponible)
SELECT c.id, 'Acetaminofen 500mg', 25.00, 30, true FROM comercios c
WHERE c.nombre = 'Farmacia Salud'
  AND NOT EXISTS (SELECT 1 FROM productos p WHERE p.nombre = 'Acetaminofen 500mg' AND p.comercio_id = c.id);
