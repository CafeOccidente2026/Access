#!/bin/sh
# Primer arranque de una base nueva (S2): el superusuario del contenedor crea el rol migrador (Flyway) y el de la
# app (sin permisos de esquema). En una base que ya existe no corre: ver la guia del README (Usuario de la base).
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
	-v migrador="$FLYWAY_USER" -v clave_migrador="$FLYWAY_PASSWORD" \
	-v app="$APP_DB_USER" -v clave_app="$APP_DB_PASSWORD" \
	-f /roles-bd.sql
