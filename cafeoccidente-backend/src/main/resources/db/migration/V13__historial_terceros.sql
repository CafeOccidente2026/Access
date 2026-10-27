-- 6b (Q8): la compra completa la direccion o el celular vacios del tercero y deja la fila en el Historial.
ALTER TABLE historial_cambios
    DROP CONSTRAINT ck_historial_cambios_entidad;
ALTER TABLE historial_cambios
    ADD CONSTRAINT ck_historial_cambios_entidad
        CHECK (entidad IN ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA', 'PERMISO', 'TERCERO'));
