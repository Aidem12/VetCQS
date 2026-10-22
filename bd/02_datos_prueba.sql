-- =============================================================
-- Sistema Veterinaria · Datos de prueba (semana 3)
-- Las contraseñas se cifran con BCrypt cuando se implemente el inicio
-- de sesión (iteración 4, semana 7). Mientras tanto quedan marcadas.
-- =============================================================

INSERT INTO usuario (nombre, usuario, contrasena_hash, rol) VALUES
 ('Ana López',      'ana',   'PENDIENTE_BCRYPT', 'CAJERO'),
 ('Dr. Luis Ruiz',  'lruiz', 'PENDIENTE_BCRYPT', 'VETERINARIO'),
 ('Carmen Díaz',    'admin', 'PENDIENTE_BCRYPT', 'ADMINISTRADOR');

INSERT INTO duenio (nombre, telefono, correo) VALUES
 ('María Hernández', '2221234567', 'maria.h@correo.com'),
 ('Jorge Ramírez',   '2227654321', NULL);

INSERT INTO mascota (duenio_id, nombre, especie, raza, sexo, fecha_nacimiento, peso_kg) VALUES
 (1, 'Max',  'Perro', 'Labrador', 'M', '2022-05-10', 18.4),
 (1, 'Luna', 'Gato',  'Mestizo',  'H', '2024-02-01', 3.9),
 (2, 'Rocky','Perro', 'Schnauzer','M', '2020-11-20', 8.1);

INSERT INTO producto (codigo_barras, nombre, tipo, precio, stock_actual, stock_minimo, maneja_caducidad) VALUES
 ('7501000000011', 'Croquetas adulto 2 kg',         'PRODUCTO', 389.00, 12, 4, FALSE),
 ('7501000000028', 'Desparasitante, tableta',       'PRODUCTO',  45.00, 40, 10, TRUE),
 ('7501000000035', 'Arena para gato 5 kg',          'PRODUCTO', 159.00,  3, 5, FALSE),
 ('7501000000042', 'Vacuna múltiple canina',        'PRODUCTO', 280.00,  8, 3, TRUE),
 (NULL,            'Consulta general',              'SERVICIO', 350.00,  0, 0, FALSE),
 (NULL,            'Aplicación de vacuna',          'SERVICIO', 120.00,  0, 0, FALSE);

INSERT INTO lote (producto_id, numero_lote, caducidad, cantidad) VALUES
 (2, 'DP-2601', '2027-03-31', 40),
 (4, 'VC-2609', '2026-12-15',  8);
