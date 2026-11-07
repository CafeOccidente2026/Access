-- 4e: "Restaurar Compras Anunciadas" (solo el Administrador) recalcula el saldo y queda en el Historial.
ALTER TABLE historial_cambios
    DROP CONSTRAINT ck_historial_cambios_entidad,
    DROP CONSTRAINT ck_historial_cambios_accion;
ALTER TABLE historial_cambios
    ADD CONSTRAINT ck_historial_cambios_entidad
        CHECK (entidad IN ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA', 'PERMISO', 'TERCERO', 'REMESA',
                           'COMPRA_ANUNCIADA')),
    ADD CONSTRAINT ck_historial_cambios_accion
        CHECK (accion IN ('CREAR', 'MODIFICAR', 'REASIGNAR', 'ACTIVAR', 'DESACTIVAR', 'PUBLICAR', 'CONCEDER', 'RETIRAR',
                          'DESMARCAR', 'RESTAURAR'));
