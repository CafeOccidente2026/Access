#!/bin/sh
# Respaldo diario de la base a las 23:00 (TZ del contenedor: America/Bogota).
# Borra los respaldos de mas de RESPALDO_DIAS dias. "sh /respaldo.sh ahora" hace uno al momento.
set -u
DIAS="${RESPALDO_DIAS:-30}"

respaldar() {
	archivo="/respaldos/${PGDATABASE}_$(date +%Y%m%d_%H%M).dump"
	if pg_dump -Fc -f "$archivo.tmp"; then
		mv "$archivo.tmp" "$archivo"
		find /respaldos -name "${PGDATABASE}_*.dump" -mtime +"$DIAS" -delete
		echo "Respaldo listo: $archivo"
	else
		rm -f "$archivo.tmp"
		echo "ERROR: fallo el respaldo de $(date)" >&2
		return 1
	fi
}

if [ "${1:-}" = "ahora" ]; then
	respaldar
	exit $?
fi

while true; do
	ahora=$(date +%s)
	siguiente=$(date -d "23:00" +%s)
	[ "$siguiente" -le "$ahora" ] && siguiente=$((siguiente + 86400))
	sleep $((siguiente - ahora))
	respaldar || true
done
