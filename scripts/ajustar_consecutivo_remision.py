"""
Pone el contador de remision/remesa de cada agencia en el mayor numero de remision o remesa que ya exista para ella
(decision del 23-nov: la numeracion sigue desde la de Access; El Tambo con TA0968 -> la proxima es SDTA-969).

Cuenta el numero del final de cada remesa y remision, en el formato de Access (TA0968 -> 968) y en los de la web
(SDTA-RP-0002 -> 2, SDTA-969 -> 969), y los consecutivos ya guardados. Nunca baja un contador.

Hay que correrlo despues de la V31 (la V31 no reconoce los numeros de Access) y despues de restaurar un respaldo
anterior a la V31, porque al volver a aplicarla el contador queda otra vez mal.

Uso:
  python scripts/ajustar_consecutivo_remision.py --base cafeoccidente_test            modo prueba: deshace al final
  python scripts/ajustar_consecutivo_remision.py --base cafeoccidente --aplicar       respaldo pg_dump y guarda

Es un script de datos de un ambiente: nunca va en Flyway. Idempotente.
"""

import argparse
import os

import psycopg2

from migrar_punto import BACKEND_DIR, backup, dotenv_values, set_remission_counter

# Mayor numero usado por agencia: el final de cada numero de remesa/remision y los consecutivos guardados.
USED = """
    SELECT agencia_id, max(n) FROM (
        SELECT agencia_id, substring(numero_remesa FROM '([0-9]+)$')::int AS n FROM remesas
        UNION ALL SELECT agencia_id, substring(numero_remision FROM '([0-9]+)$')::int FROM remisiones
        UNION ALL SELECT agencia_id, consecutivo FROM remesas
        UNION ALL SELECT agencia_id, consecutivo FROM remisiones
        UNION ALL SELECT agencia_id, numero_remision_anterior FROM remisiones
    ) usados
    WHERE n IS NOT NULL
    GROUP BY agencia_id
"""


def adjust_remission_counters(cur):
    """Sube el contador de cada agencia con remisiones o remesas. Devuelve [(agencia, nombre, prefijo, antes, despues)]."""
    cur.execute(USED)
    used = dict(cur.fetchall())
    cur.execute("SELECT a.id, a.nombre, r.prefijo, c.ultimo_consecutivo FROM agencias a"
                " LEFT JOIN registros_control r ON r.agencia_id = a.id AND r.activo"
                " LEFT JOIN consecutivos_remision_agencia c ON c.agencia_id = a.id ORDER BY a.id")
    rows = []
    for agency, name, prefix, before in cur.fetchall():
        if agency in used:
            rows.append((agency, name, prefix, before, set_remission_counter(cur, agency, used[agency])))
    return rows


def connect(base):
    env = dotenv_values(BACKEND_DIR / ".env")
    return psycopg2.connect(host="localhost", port=5433, dbname=base,
                            user=os.environ.get("DB_USER") or env["DB_USER"],
                            password=os.environ.get("DB_PASSWORD") or env["DB_PASSWORD"])


def main():
    parser = argparse.ArgumentParser(description="Contador de remision/remesa desde la numeracion de Access y la web.")
    parser.add_argument("--base", required=True, help="cafeoccidente_test, cafeoccidente u otra base local")
    parser.add_argument("--aplicar", action="store_true", help="guarda los cambios (sin esto, modo prueba)")
    args = parser.parse_args()
    if args.aplicar:
        print(f"Respaldo: {backup(args.base, 'ajustar_consecutivo_remision')}")
    conn = connect(args.base)
    try:
        with conn.cursor() as cur:
            rows = adjust_remission_counters(cur)
            for _, name, prefix, before, after in rows:
                shown = "-" if before is None else before
                print(f"  {name:<22} contador {shown:>6} -> {after:<6} proxima: {prefix or '?'}-{after + 1}")
            if not rows:
                print("  Ninguna agencia tiene remisiones ni remesas.")
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
