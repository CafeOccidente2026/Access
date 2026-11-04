-- HU-15 / Q7: un solo consecutivo de factura por punto para seco, verde y pasilla, asignado por el
-- servidor al guardar con bloqueo de fila (antes era MAX + 1 sin bloqueo y el numero lo mandaba el cliente).
CREATE TABLE consecutivos_factura (
    agencia_id bigint NOT NULL,
    ultimo_numero integer NOT NULL,
    CONSTRAINT pk_consecutivos_factura PRIMARY KEY (agencia_id),
    CONSTRAINT fk_consecutivos_factura_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id)
);

-- Semilla: el mayor numero ya usado por cada agencia en las 3 tablas.
INSERT INTO consecutivos_factura (agencia_id, ultimo_numero)
SELECT agencia_id, max(numero_factura)
FROM (SELECT agencia_id, numero_factura FROM compras_cafe_seco
      UNION ALL SELECT agencia_id, numero_factura FROM compras_cafe_verde
      UNION ALL SELECT agencia_id, numero_factura FROM compras_pasilla) compras
GROUP BY agencia_id;

-- La factura es unica por punto (cada uno tiene su resolucion DIAN), no global.
ALTER TABLE compras_cafe_seco DROP CONSTRAINT uq_compras_cafe_seco_numero_factura,
    ADD CONSTRAINT uq_compras_cafe_seco_agencia_numero_factura UNIQUE (agencia_id, numero_factura);
ALTER TABLE compras_cafe_verde DROP CONSTRAINT uq_compras_cafe_verde_numero_factura,
    ADD CONSTRAINT uq_compras_cafe_verde_agencia_numero_factura UNIQUE (agencia_id, numero_factura);
ALTER TABLE compras_pasilla DROP CONSTRAINT uq_compras_pasilla_numero_factura,
    ADD CONSTRAINT uq_compras_pasilla_agencia_numero_factura UNIQUE (agencia_id, numero_factura);
