-- "Exportar Informacion" (macro ExportarCompras): cada tabla de origen tiene su Exportado (Si/No) en
-- Access; se exportan las filas con Exportado = No y despues se marcan. Default FALSE: lo nuevo queda
-- pendiente de exportar, como en Access. Los valores reales de El Tambo los carga
-- scripts/migrate_eltambo.py (load_export_flags) desde los CSV. remission.exported ya existia (V27)
-- y grower.exported (V34).
--   COMPRAS.Exportado            -> las 5 tablas de compras
--   ANUNCIOS.Exportado           -> agency_announcement_number (una fila de ANUNCIOS por agencia)
--   [COMPRAS A FUTURO].Exportado -> future_purchase
--   INVENTARIO.Exportado         -> inventory_movement (entradas); las salidas usan remission.exported
ALTER TABLE dry_coffee_purchase ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE green_coffee_purchase ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE other_coffee_purchase ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE husk_purchase ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ferti_futuro_purchase ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agency_announcement_number ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE future_purchase ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_movement ADD COLUMN exported BOOLEAN NOT NULL DEFAULT FALSE;
