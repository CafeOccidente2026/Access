#!/bin/sh
# Reemplaza la base de produccion con un respaldo. Uso, desde deploy/: ./restaurar.sh respaldos/cafeoccidente_AAAAMMDD_HHMM.dump
set -eu
cd "$(dirname "$0")"
archivo="${1:-}"
if [ ! -f "$archivo" ]; then
	echo "Uso: $0 <archivo .dump>" >&2
	exit 1
fi

printf 'Se borra la base actual y se reemplaza con %s.\nEscriba SI para seguir: ' "$archivo"
read -r respuesta
[ "$respuesta" = "SI" ] || { echo "Cancelado."; exit 1; }

compose="docker compose -f docker-compose.prod.yml"
$compose stop backend
$compose exec -T backup sh -c 'pg_restore --clean --if-exists --no-owner -d "$PGDATABASE"' < "$archivo"
$compose start backend
echo "Base restaurada desde $archivo"
