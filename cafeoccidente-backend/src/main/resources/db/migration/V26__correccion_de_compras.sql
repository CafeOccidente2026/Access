-- HU-05 (respuesta del area, 11-nov): "Habilitacion" corrige la compra sin anularla y con el mismo numero de
-- factura. Cada compra guarda si se corrigio, cuando y quien; el detalle (antes y despues) va al Historial.
ALTER TABLE compras_cafe_seco
    ADD COLUMN corregida boolean NOT NULL DEFAULT false,
    ADD COLUMN corregida_en timestamp with time zone,
    ADD COLUMN corregida_por_usuario_id bigint,
    ADD CONSTRAINT fk_compras_cafe_seco_corregida_por FOREIGN KEY (corregida_por_usuario_id) REFERENCES usuarios(id);
ALTER TABLE compras_cafe_verde
    ADD COLUMN corregida boolean NOT NULL DEFAULT false,
    ADD COLUMN corregida_en timestamp with time zone,
    ADD COLUMN corregida_por_usuario_id bigint,
    ADD CONSTRAINT fk_compras_cafe_verde_corregida_por FOREIGN KEY (corregida_por_usuario_id) REFERENCES usuarios(id);
ALTER TABLE compras_pasilla
    ADD COLUMN corregida boolean NOT NULL DEFAULT false,
    ADD COLUMN corregida_en timestamp with time zone,
    ADD COLUMN corregida_por_usuario_id bigint,
    ADD CONSTRAINT fk_compras_pasilla_corregida_por FOREIGN KEY (corregida_por_usuario_id) REFERENCES usuarios(id);

-- Pantalla concedible "Corregir Compra": el Administrador se la da al fiel hasta que la retire (RN-02.3).
ALTER TABLE permisos DROP CONSTRAINT ck_permisos_pantalla;
ALTER TABLE permisos ADD CONSTRAINT ck_permisos_pantalla CHECK (pantalla IN
    ('ANULAR_DOCUMENTO', 'ACCESO_TABLAS', 'ACTUALIZAR_ANUNCIOS', 'REGISTRO_CONTROL', 'HISTORIAL', 'CORREGIR_COMPRA'));

-- La correccion queda en el Historial: entidad COMPRA (entidad_id = numero de factura), accion CORREGIR.
ALTER TABLE historial_cambios
    DROP CONSTRAINT ck_historial_cambios_entidad,
    DROP CONSTRAINT ck_historial_cambios_accion;
ALTER TABLE historial_cambios
    ADD CONSTRAINT ck_historial_cambios_entidad
        CHECK (entidad IN ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA', 'PERMISO', 'TERCERO', 'REMESA',
                           'COMPRA_ANUNCIADA', 'COMPRA')),
    ADD CONSTRAINT ck_historial_cambios_accion
        CHECK (accion IN ('CREAR', 'MODIFICAR', 'REASIGNAR', 'ACTIVAR', 'DESACTIVAR', 'PUBLICAR', 'CONCEDER', 'RETIRAR',
                          'DESMARCAR', 'RESTAURAR', 'CORREGIR'));
