-- Fase 7b: desmarcar una remesa (solo el Administrador, como "Desmarca Remesa" de Access) queda en el Historial.
ALTER TABLE historial_cambios
    DROP CONSTRAINT ck_historial_cambios_entidad,
    DROP CONSTRAINT ck_historial_cambios_accion;
ALTER TABLE historial_cambios
    ADD CONSTRAINT ck_historial_cambios_entidad
        CHECK (entidad IN ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA', 'PERMISO', 'TERCERO', 'REMESA')),
    ADD CONSTRAINT ck_historial_cambios_accion
        CHECK (accion IN ('CREAR', 'MODIFICAR', 'REASIGNAR', 'ACTIVAR', 'DESACTIVAR', 'PUBLICAR', 'CONCEDER', 'RETIRAR',
                          'DESMARCAR'));
