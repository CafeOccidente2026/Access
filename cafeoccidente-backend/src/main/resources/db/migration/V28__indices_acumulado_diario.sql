-- R1 (rendimiento): el acumulado diario de la retencion (GrowerDailyTotalsRepository) corre en cada compra y filtra
-- por cedula y fecha en las tres tablas de compras. Sin indice recorria la tabla entera (R0: 118.000 filas, 11-19 ms y
-- creciendo con los años).
CREATE INDEX ix_compras_cafe_seco_cedula_fecha_compra ON compras_cafe_seco (cedula, fecha_compra);
CREATE INDEX ix_compras_cafe_verde_cedula_fecha_compra ON compras_cafe_verde (cedula, fecha_compra);
CREATE INDEX ix_compras_pasilla_cedula_fecha_compra ON compras_pasilla (cedula, fecha_compra);
