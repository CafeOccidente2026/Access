-- HU-51 (RN-28.1.5): la persona juridica registra la Razon Social y se clasifica como Asociacion o Fundacion.
ALTER TABLE terceros
    ADD COLUMN razon_social character varying(100),
    ADD COLUMN clasificacion character varying(20),
    ADD CONSTRAINT ck_terceros_clasificacion CHECK (clasificacion IN ('ASOCIACION', 'FUNDACION'));
