-- HU-07 (RF-06). kg_excelso_copia no la usaba ningun calculo; el Umbral de Access (umbralespeciales,
-- 93,33 en El Tambo y segun el area de compras) no tenia columna.
ALTER TABLE registros_control DROP COLUMN kg_excelso_copia;
ALTER TABLE registros_control ADD COLUMN umbral_cafes_especiales numeric(5,2) NOT NULL DEFAULT 93.33;

-- RN-06.7: valores de cafe verde. Access no los tenia y el area de compras aun no los da: quedan en 0
-- y Compras Cafe Verde no deja registrar mientras esten en 0.
ALTER TABLE registros_control
    ADD COLUMN peso_tot_alm_cv numeric(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN peso_tot_pasilla_cv numeric(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN peso_alm_sana_cv numeric(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN pr_alm_sana_cv numeric(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN pr_alm_defec_cv numeric(15,2) NOT NULL DEFAULT 0;

-- Cada alta manda sus valores.
ALTER TABLE registros_control
    ALTER COLUMN umbral_cafes_especiales DROP DEFAULT,
    ALTER COLUMN peso_tot_alm_cv DROP DEFAULT,
    ALTER COLUMN peso_tot_pasilla_cv DROP DEFAULT,
    ALTER COLUMN peso_alm_sana_cv DROP DEFAULT,
    ALTER COLUMN pr_alm_sana_cv DROP DEFAULT,
    ALTER COLUMN pr_alm_defec_cv DROP DEFAULT;

-- D1: la semilla de Buesaco tenia FactorBase (94) en kg_excelso y el Umbral (93,33) en factor_base.
UPDATE registros_control SET kg_excelso = 70, factor_base = 94.00 WHERE agencia_id = 1 AND kg_excelso = 94;

-- D13: nombres de los fondos segun el RF.
UPDATE fondos SET nombre = 'Recursos Propios' WHERE codigo = 'RP';
UPDATE fondos SET nombre = 'Línea de Financiamiento' WHERE codigo = 'LF';
