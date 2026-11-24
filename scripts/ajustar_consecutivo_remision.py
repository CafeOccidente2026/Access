"""
Pone el contador de remision/remesa de cada agencia en el mayor numero de remision o remesa que ya exista para ella
(decision del 23-nov: la numeracion sigue desde la de Access; El Tambo con TA0968 -> la proxima es SDTA-969).

Cuenta el numero del final de cada remesa y remision, en el formato de Access (TA0968 -> 968) y en los de la web
(SDTA-RP-0002 -> 2, SDTA-969 -> 969), y los consecutivos ya guardados. Ignora los numeros sueltos que saltan muy por
encima de la numeracion del punto (CO9999 en Consaca, 24-nov; ver normal_remission_last). Nunca baja un contador.

Hay que correrlo despues de la V31 (la V31 no reconoce los numeros de Access) y despues de restaurar un respaldo
anterior a la V31, porque al volver a aplicarla el contador queda otra vez mal.

Uso:
  python scripts/ajustar_consecutivo_remision.py --base cafeoccidente_test            modo prueba: deshace al final
  python scripts/ajustar_consecutivo_remision.py --base cafeoccidente --aplicar       respaldo pg_dump y guarda

Es un script de datos de un ambiente: nunca va en Flyway. Idempotente.
"""

import argparse
import os
from datetime import date

import psycopg2

from migrar_punto import BACKEND_DIR, backup, dotenv_values, normal_remission_last, set_remission_counter

# Numeros usados por agencia: el final de cada numero de remesa/remision y los consecutivos guardados, con el texto y
# la fecha (de la remesa o de la remision) para mostrar cual queda como ultima.
USED = """
    SELECT agencia_id, n, max(texto), max(fecha) FROM (
        SELECT agencia_id, substring(numero_remesa FROM '([0-9]+)$')::int AS n, numero_remesa AS texto,
               creado_en::date AS fecha FROM remesas
        UNION ALL SELECT agencia_id, substring(numero_remision FROM '([0-9]+)$')::int, numero_remision, fecha_remision
            FROM remisiones
        UNION ALL SELECT agencia_id, consecutivo, numero_remesa, creado_en::date FROM remesas
        UNION ALL SELECT agencia_id, consecutivo, numero_remision, fecha_remision FROM remisiones
        UNION ALL SELECT agencia_id, numero_remision_anterior, numero_remision, fecha_remision FROM remisiones
    ) usados
    WHERE n IS NOT NULL
    GROUP BY agencia_id, n
"""


def adjust_remission_counters(cur):
    """Sube el contador de cada agencia con remisiones o remesas hasta el ultimo numero de su numeracion normal (sin
    los numeros sueltos, como CO9999). Devuelve [(agencia, nombre, prefijo, antes, despues, detalle)]."""
    cur.execute(USED)
    used = {}
    for agency, number, text, day in cur.fetchall():
        used.setdefault(agency, {})[number] = (text, day)
    cur.execute("SELECT a.id, a.nombre, r.prefijo, c.ultimo_consecutivo FROM agencias a"
                " LEFT JOIN registros_control r ON r.agencia_id = a.id AND r.activo"
                " LEFT JOIN consecutivos_remision_agencia c ON c.agencia_id = a.id ORDER BY a.id")
    rows = []
    for agency, name, prefix, before in cur.fetchall():
        if agency not in used:
            continue
        numbers = used[agency]
        last, ignored = normal_remission_last(numbers)
        normal = [n for n in numbers if n not in ignored]
        by_date = max(normal, key=lambda n: (numbers[n][1] or date.min, n))
        detail = {"by_number": numbers[last], "by_date": numbers[by_date], "ignored": ignored,
                  "ignored_texts": [numbers[n] for n in ignored]}
        rows.append((agency, name, prefix, before, set_remission_counter(cur, agency, last), detail))
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
            for _, name, prefix, before, after, detail in rows:
                shown = "-" if before is None else before
                print(f"  {name:<22} contador {shown:>6} -> {after:<6} proxima: {prefix or '?'}-{after + 1}")
                print(f"  {'':<22} ultima por numero {detail['by_number'][0]} ({detail['by_number'][1]}),"
                      f" ultima por fecha {detail['by_date'][0]} ({detail['by_date'][1]})")
                for text, day in detail["ignored_texts"]:
                    print(f"  {'':<22} {text} ({day}) ignorada: numero suelto, no sigue la numeracion del punto")
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
