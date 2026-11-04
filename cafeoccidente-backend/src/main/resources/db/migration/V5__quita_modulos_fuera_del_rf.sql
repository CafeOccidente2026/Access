-- Q5 (area de compras, 2026-10-22): se eliminan Compras Cafes Otros, Fertifuturo y Exportar/Importar/
-- Exportado Especial (la base ya es centralizada). Las dos tablas estaban vacias.
DELETE FROM inventarios WHERE modulo_compra IN ('OTHER_COFFEE', 'FERTI_FUTURO');
DELETE FROM formas_pago_compras WHERE origen IN ('OTHER', 'FERTI');
DELETE FROM cheques_relacionados WHERE origen IN ('OTHER', 'FERTI');
DROP TABLE compras_cafes_otros;
DROP TABLE compras_ferti_futuro;

-- En las compras, exportado solo servia para Exportar y para impedir la anulacion. Se conserva el
-- bloqueo con su propio nombre: las compras cerradas en Access no se anulan; las nuevas nacen en false.
ALTER TABLE compras_cafe_seco RENAME COLUMN exportado TO anulacion_bloqueada;
ALTER TABLE compras_cafe_verde RENAME COLUMN exportado TO anulacion_bloqueada;
ALTER TABLE compras_pasilla RENAME COLUMN exportado TO anulacion_bloqueada;

-- Solo las usaba Exportar. remisiones.exportado (Remesa, HU-14) y terceros.exportado (RF-28.3) se quedan.
ALTER TABLE compras_anunciadas DROP COLUMN exportado;
ALTER TABLE inventarios DROP COLUMN exportado;
ALTER TABLE anuncios_agencias DROP COLUMN exportado;
