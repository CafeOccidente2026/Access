-- VrIncCalidad ("Pr IncCalidad"): columna real en ANUNCIOS del legado (confirmada en
-- anuncios_migrar.csv, columna vrinccalidad) - se propaga a "Compras a Futuro" via las macros
-- "Asignar numero anuncio * PFuture Buys" (Texto45 = ÚltimoDeVrIncCalidad) y se usa en la cascada
-- de FERTIFUTURO (var1 = var5 + IncCalidad + Bonificacion, Form_FERTIFUTURO.bas). No se encontro
-- ningun control para este campo en "ANUNCIOS CORRF" (la pantalla real de "Actualizar Anuncio con
-- Factor") - el legado debia editarlo por otra via no identificada en el export VBA disponible.
-- Se expone igual en nuestra pantalla de anuncios (AnnouncementRequest) por ser un dato real de la
-- tabla maestra, nullable porque los anuncios historicos/existentes no lo tienen poblado.
ALTER TABLE announcement ADD COLUMN quality_increment NUMERIC(15, 2);
