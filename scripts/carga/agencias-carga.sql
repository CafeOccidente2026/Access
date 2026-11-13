-- Parte R (R4 en adelante): deja las 22 agencias listas para facturar en la prueba de carga.
-- SOLO para el compose de prueba (cafeoccidente-pruebaprod), despues de datos-sinteticos.sql.
--   * Registro de Control activo: copia el de El Tambo (agencia 4) con su propio prefijo.
--   * Anuncios: los que tienen asignados Buesaco 1 (ESTANDAR, CV, PASILLA) y El Tambo (especiales).
--   * Un usuario USUARIO por agencia (carga_<id>) con el permiso de Acceso a Tablas; la clave llega como :hash (bcrypt).
-- Uso: psql -v ON_ERROR_STOP=1 -v hash="'<bcrypt>'" -U <superusuario> -d cafeoccidente -f agencias-carga.sql

BEGIN;

DO $$
DECLARE cols text;
BEGIN
  SELECT string_agg(format('%I', column_name), ', ' ORDER BY ordinal_position) INTO cols
  FROM information_schema.columns
  WHERE table_name = 'registros_control' AND column_name NOT IN ('id', 'agencia_id', 'prefijo', 'punto_compra');
  EXECUTE format(
    'INSERT INTO registros_control (agencia_id, prefijo, punto_compra, %1$s)
     SELECT a.id, ''C'' || lpad(a.id::text, 3, ''0''), a.nombre, %2$s
     FROM registros_control r CROSS JOIN agencias a
     WHERE r.agencia_id = 4 AND r.activo
       AND NOT EXISTS (SELECT 1 FROM registros_control x WHERE x.agencia_id = a.id AND x.activo)',
    cols, (SELECT string_agg('r.' || c, ', ') FROM unnest(string_to_array(cols, ', ')) c));
END $$;

INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)
SELECT a.id, aa.anuncio_id, aa.numero_anuncio, aa.fecha_asignacion
FROM anuncios_agencias aa CROSS JOIN agencias a
WHERE aa.agencia_id IN (1, 4) AND a.id <> aa.agencia_id
ON CONFLICT (agencia_id, numero_anuncio) DO NOTHING;

INSERT INTO usuarios (usuario, contrasena_hash, activo, rol_id, agencia_id)
SELECT 'carga_' || a.id, :hash, true, (SELECT id FROM roles WHERE nombre = 'USUARIO'), a.id
FROM agencias a
ON CONFLICT DO NOTHING;

INSERT INTO permisos (usuario_id, pantalla, concedido_por, concedido_en)
SELECT u.id, 'ACCESO_TABLAS', (SELECT min(id) FROM usuarios WHERE rol_id = (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR')), now()
FROM usuarios u WHERE u.usuario LIKE 'carga\_%'
ON CONFLICT DO NOTHING;

COMMIT;
