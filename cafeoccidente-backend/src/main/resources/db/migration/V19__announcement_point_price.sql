-- Pr_AlmSana en Form_ANUNCIOS PASILLA.bas (etiqueta real "Pr Punto"): a diferencia de
-- Form_ANUNCIOS CORRF.bas, ese formulario nunca tuvo Texto17_AfterUpdate/Costos_LostFocus que lo
-- derivara de Pr_Base_CPS/BaseCarga - el admin lo digitaba directo. Se agrega crudo y separado de
-- basePriceLoad para que "Actualizar Anuncio Pasilla" lo guarde tal cual, sin la formula de CORRF.
ALTER TABLE announcement ADD COLUMN point_price NUMERIC(15, 2);
