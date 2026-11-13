-- Parte R (rendimiento): 5 años de compras con el promedio de El Tambo (agencia 4) en las 22 agencias.
-- SOLO para el compose de prueba (cafeoccidente-pruebaprod), nunca para la base del usuario ni producción.
-- Parte de una copia de cafeoccidente_test: usa las compras de El Tambo como molde (precios, kilos, productos).
--   * Café seco: 3 compras por agencia y por día desde 2022-01-01 hasta ayer (~1.090 al año, el ritmo de El Tambo).
--   * Cada agencia tiene ~1.200 vendedores propios tomados de terceros; 2 % de las compras quedan ANULADA.
--   * Una forma de pago (efectivo) por compra.
--   * Remesas semanales: las compras VALIDA de semanas cerradas quedan en su remesa; la semana actual queda pendiente.
--   * Caja, empaques y suministros: los movimientos de El Tambo copiados a cada agencia y a cada año.
-- Uso: psql -v ON_ERROR_STOP=1 -U <superusuario> -d cafeoccidente -f datos-sinteticos.sql

\set desde '2022-01-01'

BEGIN;

CREATE TEMP TABLE molde AS
SELECT row_number() OVER (ORDER BY id) - 1 AS n, c.*
FROM compras_cafe_seco c WHERE c.agencia_id = 4;

CREATE TEMP TABLE vendedores AS
SELECT row_number() OVER (ORDER BY identificacion) - 1 AS n, identificacion, primer_nombre, primer_apellido
FROM terceros WHERE primer_nombre IS NOT NULL AND primer_apellido IS NOT NULL;

-- Compras: agencia x dia x 3. El molde y el vendedor salen de una mezcla fija (repetible).
CREATE TEMP TABLE nuevas AS
SELECT a.id AS agencia_id, d::date AS fecha, k,
       row_number() OVER (PARTITION BY a.id ORDER BY d, k) AS orden,
       abs(hashtext(a.id || '-' || d::date || '-' || k)) AS h
FROM agencias a
CROSS JOIN generate_series(:'desde'::date, current_date - 1, interval '1 day') d
CROSS JOIN generate_series(1, 3) k;

INSERT INTO compras_cafe_seco (fecha_compra, agencia_id, fondo_id, especial, producto_id, numero_anuncio, fecha_anuncio,
    pr_base_cps, cedula, nombres, apellidos, tipo_vendedor, direccion, celular, sacos, kilos_brutos, destare, kilos_netos,
    peso_tot_alm, porc_merma, peso_tot_pasilla, porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec,
    bonificacion, castigo, costos, vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo,
    exento_retencion, retefuente, descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago, numero_cheque,
    creado_por_usuario_id, creado_en, numero_factura, anulacion_bloqueada, estado, corregida)
SELECT x.fecha, x.agencia_id, m.fondo_id, m.especial, m.producto_id, m.numero_anuncio, x.fecha,
    m.pr_base_cps, v.identificacion, v.primer_nombre, v.primer_apellido, m.tipo_vendedor, m.direccion, m.celular,
    m.sacos, m.kilos_brutos, m.destare, m.kilos_netos, m.peso_tot_alm, m.porc_merma, m.peso_tot_pasilla,
    m.porc_kg_pasilla, m.peso_alm_sana, m.factor, m.pr_alm_sana, m.pr_alm_defec, m.bonificacion, m.castigo, m.costos,
    m.vr_kilo, m.vr_bruto, m.vr_inventario, m.aporte_asociado, m.descuento_cooperativo, m.exento_retencion,
    m.retefuente, m.descuento_financiero, m.otros_descuentos, m.neto_a_pagar, 'EFECTIVO', NULL,
    1, x.fecha + time '08:00' + (x.k * interval '2 hour'), 100000 + x.orden, false,
    CASE WHEN x.h % 50 = 0 THEN 'ANULADA' ELSE 'VALIDA' END, false
FROM nuevas x
JOIN molde m ON m.n = x.h % (SELECT count(*) FROM molde)
JOIN vendedores v ON v.n = (x.agencia_id * 1237 + (x.h / 7) % 1200) % (SELECT count(*) FROM vendedores);

INSERT INTO formas_pago_compras (origen, origen_id, efectivo, cheque, transferencia, datafono)
SELECT 'DRY', c.id, c.neto_a_pagar, 0, 0, 0
FROM compras_cafe_seco c
WHERE c.numero_factura >= 100000
  AND NOT EXISTS (SELECT 1 FROM formas_pago_compras f WHERE f.origen = 'DRY' AND f.origen_id = c.id);

-- Remesas semanales (semanas cerradas) y sus compras.
INSERT INTO remesas (agencia_id, fondo_id, consecutivo, numero_remesa, cantidad_empaques, tipo_empaque, estado,
    creado_por_usuario_id, creado_en)
SELECT s.agencia_id, 2, row_number() OVER (PARTITION BY s.agencia_id ORDER BY s.semana),
    'S' || to_char(s.semana, 'IYYYIW'), 10, 'NUEVO', 'VIGENTE', 1, s.semana + interval '6 day 17 hour'
FROM (SELECT DISTINCT agencia_id, date_trunc('week', fecha_compra)::date AS semana
      FROM compras_cafe_seco WHERE numero_factura >= 100000
        AND date_trunc('week', fecha_compra) < date_trunc('week', current_date)) s;

UPDATE compras_cafe_seco c SET remesa_id = r.id
FROM remesas r
WHERE c.numero_factura >= 100000 AND c.estado = 'VALIDA'
  AND r.agencia_id = c.agencia_id AND r.numero_remesa = 'S' || to_char(date_trunc('week', c.fecha_compra), 'IYYYIW');

-- Caja, empaques y suministros: los de El Tambo en cada agencia y en cada año.
INSERT INTO movimientos_caja (id_transaccion, agencia_id, fondo_id, fecha, cedula, detalle, entradas, salidas,
    forma_pago, numero_cheque, creado_en)
SELECT m.id_transaccion, a.id, m.fondo_id, (m.fecha - make_interval(years => y))::date, m.cedula, m.detalle,
    m.entradas, m.salidas, m.forma_pago, m.numero_cheque, m.creado_en - make_interval(years => y)
FROM movimientos_caja m CROSS JOIN agencias a CROSS JOIN generate_series(0, 4) y
WHERE m.agencia_id = 4 AND NOT (a.id = 4 AND y = 0);

INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula, detalle, entradas, salidas, creado_en)
SELECT e.id_transaccion, a.id, e.tipo_empaque, (e.fecha - make_interval(years => y))::date, e.cedula, e.detalle,
    e.entradas, e.salidas, e.creado_en - make_interval(years => y)
FROM empaques e CROSS JOIN agencias a CROSS JOIN generate_series(0, 4) y
WHERE e.agencia_id = 4 AND NOT (a.id = 4 AND y = 0);

INSERT INTO suministros (id_transaccion, agencia_id, fondo_id, fecha, cedula, detalle, entradas, salidas,
    forma_pago, numero_cheque, creado_en)
SELECT s.id_transaccion, a.id, s.fondo_id, (s.fecha - make_interval(years => y))::date, s.cedula, s.detalle,
    s.entradas, s.salidas, s.forma_pago, s.numero_cheque, s.creado_en - make_interval(years => y)
FROM suministros s CROSS JOIN agencias a CROSS JOIN generate_series(0, 4) y
WHERE s.agencia_id = 4 AND NOT (a.id = 4 AND y = 0);

COMMIT;

ANALYZE;
