"""
Pone el prefijo del punto al numero de anuncio de las compras cargadas de Access (decision del 18-nov).

Las compras y compras a futuro que cargo migrar_punto.py antes de este cambio guardan el anuncio como "3490"; la web
lo guarda como "SDTA-3490" (prefijo del Registro de Control activo de la agencia). Con el numero sin prefijo, Kilos
Entregados de los cupos no las suma y en pantalla no se ven como "TA-3490" (R4).

Uso:
  python scripts/corregir_anuncio_prefijo.py --base cafeoccidente_test            modo prueba: deshace al final
  python scripts/corregir_anuncio_prefijo.py --base cafeoccidente --aplicar       respaldo pg_dump y guarda

Es un script de datos de un ambiente: nunca va en Flyway. Idempotente: solo toca los numeros que son solo digitos.
"""

import argparse
import os

import psycopg2

from migrar_punto import BACKEND_DIR, backup, dotenv_values

# Tablas que guardan el numero de anuncio de display ("PREFIJO-numero").
TABLES = ("compras_cafe_seco", "compras_cafe_verde", "compras_pasilla", "compras_anunciadas")


def fix_announcement_prefixes(cur):
    """Antepone el prefijo de la agencia a los numeros de anuncio que son solo digitos. Devuelve {tabla: filas}."""
    changed = {}
    for table in TABLES:
        cur.execute(
            f"UPDATE {table} t SET numero_anuncio = r.prefijo || '-' || t.numero_anuncio"
            " FROM registros_control r"
            " WHERE r.agencia_id = t.agencia_id AND r.activo AND t.numero_anuncio ~ '^[0-9]+$'"
        )
        changed[table] = cur.rowcount
    return changed


def without_prefix(cur):
    """Filas que siguen sin prefijo (agencias sin Registro de Control activo): {tabla: filas}."""
    left = {}
    for table in TABLES:
        cur.execute(f"SELECT count(*) FROM {table} WHERE numero_anuncio ~ '^[0-9]+$'")
        left[table] = cur.fetchone()[0]
    return left


def connect(base):
    env = dotenv_values(BACKEND_DIR / ".env")
    return psycopg2.connect(host="localhost", port=5433, dbname=base,
                            user=os.environ.get("DB_USER") or env["DB_USER"],
                            password=os.environ.get("DB_PASSWORD") or env["DB_PASSWORD"])


def main():
    parser = argparse.ArgumentParser(description="Prefijo del punto en el numero de anuncio de las compras de Access.")
    parser.add_argument("--base", required=True, help="cafeoccidente_test, cafeoccidente u otra base local")
    parser.add_argument("--aplicar", action="store_true", help="guarda los cambios (sin esto, modo prueba)")
    args = parser.parse_args()
    if args.aplicar:
        print(f"Respaldo: {backup(args.base, 'corregir_anuncio_prefijo')}")
    conn = connect(args.base)
    try:
        with conn.cursor() as cur:
            for table, rows in fix_announcement_prefixes(cur).items():
                print(f"  {table}: {rows} con prefijo")
            for table, rows in without_prefix(cur).items():
                if rows:
                    print(f"  {table}: {rows} siguen sin prefijo (agencia sin Registro de Control activo)")
        if args.aplicar:
            conn.commit()
            print("Cambios guardados.")
        else:
            conn.rollback()
            print("Modo prueba (sin --aplicar): no se guardo nada.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
