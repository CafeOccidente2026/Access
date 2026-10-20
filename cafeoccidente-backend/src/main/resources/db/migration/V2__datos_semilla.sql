-- Datos semilla en español: las mismas filas (con sus ids) que dejaban V1-V37.
-- Incluye datos de prueba que se limpian en su HU (D13 del mapeo).

INSERT INTO agencias (id, nombre, activa, nombre_access, prefijo_factura) VALUES
    (1, 'Buesaco', true, 'BUESACO1 OCCIDENTE', 'SDBU'),
    (2, 'Ancuya', true, 'ANCUYA', 'DCAN'),
    (3, 'Consaca', true, 'CONSACA', 'SDCO'),
    (4, 'El Tambo', true, 'EL TAMBO', 'SDTA'),
    (5, 'La Florida', true, 'LA FLORIDA', 'SDFL'),
    (6, 'Linares', true, 'LINARES', 'SDLI'),
    (7, 'Pasto Sede', true, 'PASTO', 'SDPA'),
    (8, 'Pasto Las Lunas', true, 'PASTO LAS LUNAS', 'SDLU'),
    (9, 'Samaniego', true, 'SAMANIEGO', 'SDSSA'),
    (10, 'Sandoná', true, 'SANDONA', 'SDSN'),
    (11, 'Los Andes (Sotomayor)', true, 'LOS ANDES', 'SDSO'),
    (12, 'Chachagüí', true, 'CHACHAGUI', 'DCH'),
    (13, 'El Ingenio (Sandoná)', true, 'EL INGENIO', 'SDIN'),
    (14, 'El Peñol', true, 'EL PEÑOL', 'SDPE'),
    (15, 'Matituy', true, 'MATITUY', 'SDMA'),
    (16, 'Tunja', true, 'TUNJA', 'SDTJ'),
    (17, 'Santa María (Buesaco)', true, 'SANTA MARIA', 'SDSM'),
    (18, 'Yacuanquer', true, 'YACUANQUER', 'SDYA'),
    (19, 'Funes', true, 'FUNES', 'SDFU'),
    (20, 'Mallama', true, 'MALLAMA', 'SDML');

INSERT INTO registros_control (id, activo, numero_registro_control, kg_excelso, base_retencion, base_carga, porcentaje_retencion, base_pasilla, porc_kg_pasilla_promedio, punto_compra, prefijo, costos, tamano_muestra, kg_excelso_copia, porcentaje_cafe_verde, factor_base, porc_aportes_asociados, porc_desc_coop_no_asociados, cedula_fiel, resolucion_dian, fecha_resolucion, desde_resolucion, hasta_resolucion, vigencia, agencia_id) VALUES
    (1, true, 2, 94, 8379840.00, 125, 0.50, 12.50, 6.14, 'BUESACO1 OCCIDENTE', 'SDBU', 692.00, 250.00, 70.00, 50.00, 93.33, 2.00, 0.80, '1061699481', '18764112191428', '2026-07-06'::date, 27867, 31567, 24, 1),
    (2, true, 2, 70, 8379840.00, 125, 0.50, 12.50, 6.14, 'EL TAMBO', 'SDTA', 698.00, 250.00, 70.00, 50.00, 94.00, 2.00, 0.80, '87304051', '18764112142534', '2026-07-04'::date, 43754, 48753, 24, 4);

INSERT INTO fondos (id, codigo, nombre, activo) VALUES
    (1, 'RP', 'Resolucion Propia', true),
    (2, 'LF', 'Libre Formacion', true);

INSERT INTO terceros (id, identificacion, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido, agencia_id, fecha_nacimiento, direccion, telefono, fecha_afiliacion, tipo, activo, fallecido, retirado, empresa_transporte, vehiculo, actualizado_en, numero_registro, exportado, nuevo, pais, sexo, asociacion, aceptado, habil, estado_civil, lugar_nacimiento, cedula_cafetera, acta, observacion, ciudad, codigo_postal, email) VALUES
    (1, '123456', 'Juan', NULL, 'Perez', 'Gomez', 1, '1975-03-10'::date, 'Vereda El Diviso', '3001234567', '2005-06-01'::date, 'S', true, false, false, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, '789012', 'Maria', 'Isabel', 'Rodriguez', NULL, 1, '1982-11-22'::date, 'Vereda La Cocha', '3009876543', '2010-02-15'::date, 'C', true, false, false, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    (3, '345678', 'Carlos', NULL, 'Martinez', 'Lopez', 1, '1950-07-04'::date, 'Vereda San Isidro', '3005551234', '1998-09-20'::date, 'S', false, true, false, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO productos (id, especial, fondo_id, codigo, nombre) VALUES
    (1, 'RN', 1, '0110002000002', 'CARE R.P. RN'),
    (2, 'RN', 2, '0110001000008', 'CAFÉ LFTO RN'),
    (3, 'RAINFOREST', 2, '0110001000016', 'CAFÉ LFTO RAIN'),
    (4, 'RAINFOREST', 1, '0110002000010', 'CAFÉ RP RAIN'),
    (5, 'RAIN TE', 1, '0110002000011', 'CAFÉ RP RAIN TE'),
    (6, 'RAIN TE', 2, '0110001000017', 'CAFÉ LF RAIN TE'),
    (7, 'NESS TE', 1, '0110002000012', 'CAFÉ RP NESS TE'),
    (8, 'NESS TE', 2, '0110001000018', 'CAFÉ LF NESS TE'),
    (9, 'NESPRESSO - FTUSA', 2, '0110001000007', 'CAFÉ LFTO NESS - FTUSA'),
    (10, 'NESPRESSO - FTUSA', 1, '0110002000001', 'CAFÉ R.P. NESS - FTUSA'),
    (11, 'EXPON', 1, '0110002000018', 'CAFÉ RP EXPON'),
    (12, 'EXPOR', 1, '0110002000019', 'CAFÉ RP EXPOR'),
    (13, 'REGIONAL NARIÑO 4C', 1, '0110002000017', 'CAFÉ RP RN4C'),
    (14, 'REGIONAL NARIÑO 4C', 2, '0110001000023', 'CAFÉ LF RN4C'),
    (15, 'COLOMBIA TIERRA DE DIVERSIDAD NESS', 2, '0110001000033', 'CAFÉ LF COL TIERRA DE DIVERSIDAD NESS'),
    (16, 'COLOMBIA TIERRA DE DIVERSIDAD NESS', 1, '0110002000033', 'CAFÉ RP COL TIERRA DE DIVERSIDAD NESS'),
    (17, 'COLOMBIA TIERRA DE DIVERSIDAD RN', 2, '0110001000038', 'CAFÉ LF COL TIERRA DE DIVERSIDAD RN'),
    (18, 'COLOMBIA TIERRA DE DIVERSIDAD RN', 1, '0110002000038', 'CAFÉ RP COL TIERRA DE DIVERSIDAD RN'),
    (19, 'NESPRESSO LATE HARVEST - FTUSA', 2, '0110001000024', 'CAFÉ LF NESSLH'),
    (20, 'NESPRESSO LATE HARVEST - FTUSA', 1, '0110002000005', 'CAFÉ RP NESSLH - FTUSA'),
    (21, 'ESTANDAR FED', 1, '0110002000015', 'CAFÉ RP ESTANDAR FEDERACION'),
    (22, 'ESTANDAR FED', 2, '0110001000021', 'CAFÉ LF ESTANDAR FEDERACION'),
    (23, 'ESTANDAR', 1, '0110002000016', 'CAFÉ RP ESTANDAR'),
    (24, 'ESTANDAR', 2, '0110001000022', 'CAFÉ LF ESTANDAR'),
    (25, 'REGIONAL NARIÑO DIFERENCIADO 85 - 85.9', 2, '0110001000032', 'CAFÉ LF RN DIFERENCIADO 85 - 85.9'),
    (26, 'REGIONAL NARIÑO DIFERENCIADO 85 - 85.9', 1, '0110002000032', 'CAFÉ RP RN DIFERENCIADO 85 - 85.9'),
    (27, 'REGIONAL NARIÑO DIFERENCIADO 84 - 84.9', 1, '0110002000037', 'CAFÉ RP RN DIFERENCIADO 84 - 84.9'),
    (28, 'REGIONAL NARIÑO DIFERENCIADO 84 - 84.9', 2, '0110001000037', 'CAFÉ LF RN DIFERENCIADO 84 - 84.9'),
    (29, 'ILLY', 1, '0110002000028', 'CAFÉ RP ILLY'),
    (30, 'ILLY', 2, '0110001000028', 'CAFÉ LF ILLY'),
    (31, 'MICROLNESS', 1, '0110002000023', 'CAFÉ RP MICROLNESS'),
    (32, 'MICROLOTES 88+', 1, '0110002000035', 'CAFÉ RP MICROLOTES 88+'),
    (33, 'MICROLOTES 88+', 2, '0110001000035', 'CAFÉ LF MICROLOTES 88+'),
    (34, 'MICROLOTES 86 - 87.9', 1, '0110002000036', 'CAFÉ RP MICROLOTES 86 - 87.9'),
    (35, 'MICROLOTES 86 - 87.9', 2, '0110001000036', 'CAFÉ LF MICROLOTES 86 - 87.9'),
    (36, 'NESPRESSO CON TAZA - FTUSA', 1, '0110002000030', 'CAFÉ RP NESPRESSO CON TAZA - FTUSA'),
    (37, 'NESPRESSO CON TAZA - FTUSA', 2, '0110001000030', 'CAFÉ LF NESPRESSO CON TAZA - FTUSA'),
    (38, 'NESPRESSO LATE HARVEST CON TAZA - FTUSA', 1, '0110002000031', 'CAFÉ RP NESSLH CON TAZA - FTUSA'),
    (39, 'NESPRESSO LATE HARVEST CON TAZA - FTUSA', 2, '0110001000031', 'CAFÉ LF NESSLH CON TAZA - FTUSA'),
    (40, 'CV', 1, '0110002000004', 'CAFÉ VERDE'),
    (41, 'PASILLA', 1, '0110002000008', 'PASILLA RP'),
    (42, 'PASILLA', 2, '0110001000013', 'CAFÉ LF EN'),
    (43, 'CORR', 1, '0110002000003', 'CAFÉ R.P. CORR'),
    (44, 'CORR', 2, '0110001000009', 'CAFÉ LFTO CORR'),
    (45, 'TE', 1, '0110002000007', 'CAFÉ RP TE'),
    (46, 'TE', 2, '0110001000012', 'CAFÉ LF TE');

INSERT INTO roles (id, nombre) VALUES
    (1, 'ADMIN'),
    (2, 'USER');

INSERT INTO anuncios (id, numero_anuncio, fecha_anuncio, pr_base_cps, agencia_id, fondo_id, activo, pr_alm_defec, pr_alm_sana, bonificacion, costos, especial, sobrepr_carga_esp, pr_punto, pr_inc_calidad) VALUES
    (1, '1', '2026-10-20'::date, 1200000.00, 1, 1, true, 8000.00, 12000.00, 100.00, 692.00, 'ESTANDAR', NULL, NULL, NULL),
    (2, '1', '2026-10-20'::date, 1200000.00, 1, 1, true, 0.00, 1200000.00, 0.00, 692.00, 'CV', NULL, NULL, NULL),
    (3, '1', '2026-10-20'::date, 1200000.00, 1, 1, true, 0.00, 1200000.00, 0.00, 692.00, 'PASILLA', NULL, NULL, NULL);

INSERT INTO anuncios_agencias (id, agencia_id, anuncio_id, numero_anuncio, fecha_asignacion, exportado) VALUES
    (1, 1, 1, 1, '2026-10-20'::date, false),
    (2, 1, 2, 2, '2026-10-20'::date, false),
    (3, 1, 3, 3, '2026-10-20'::date, false);

-- Las secuencias quedan donde las dejaban V1-V37 (algunas tablas se borraron y resembraron).
SELECT setval('anuncios_agencias_id_seq', 3, true);
SELECT setval('agencias_id_seq', 20, true);
SELECT setval('anuncios_id_seq', 3, true);
SELECT setval('registros_control_id_seq', 2, true);
SELECT setval('fondos_id_seq', 2, true);
SELECT setval('terceros_id_seq', 3, true);
SELECT setval('productos_id_seq', 46, true);
SELECT setval('roles_id_seq', 2, true);
