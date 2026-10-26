"""
Limpieza de una vez: las agencias duplicadas que creaba migrate_eltambo.py antes de AGENCY_ALIASES.

Pasa los terceros de cada agencia alias a su agencia real, borra la alias y deja nombre y estado de las
EXTRA_AGENCIES. Todo en una transaccion. Aborta si una agencia alias tiene filas fuera de terceros.

    python scripts/limpiar_agencias_duplicadas.py            # solo muestra lo que haria
    python scripts/limpiar_agencias_duplicadas.py --aplicar  # escribe

DB_NAME del entorno elige la base, como en migrate_eltambo.py.
"""

import os
import sys

import psycopg2
from dotenv import dotenv_values

from migrate_eltambo import AGENCY_ALIASES, BACKEND_DIR, EXTRA_AGENCIES, insert_suggested_prefixes


def other_references(cur):
    """(tabla, columna) que apuntan a agencias, salvo terceros."""
    cur.execute(
        "SELECT c.conrelid::regclass::text, a.attname FROM pg_constraint c"
        " JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)"
        " WHERE c.contype = 'f' AND c.confrelid = 'agencias'::regclass"
    )
    return [(table, column) for table, column in cur.fetchall() if table != "terceros"]


def count(cur, sql, *params):
    cur.execute(sql, params)
    return cur.fetchone()[0]


def clean(cur):
    cur.execute("SELECT nombre_access, id FROM agencias WHERE nombre_access IS NOT NULL")
    by_access = dict(cur.fetchall())
    references = other_references(cur)
    growers = count(cur, "SELECT count(*) FROM terceros")
    print(f"Antes: {count(cur, 'SELECT count(*) FROM agencias')} agencias, {growers} terceros")

    moved = 0
    for alias, target in sorted(AGENCY_ALIASES.items()):
        alias_id = by_access.get(alias)
        if alias_id is None:
            continue
        target_id = by_access.get(target)
        if target_id is None:
            raise SystemExit(f"No existe la agencia real {target!r} de {alias!r}")
        for table, column in references:
            if count(cur, f"SELECT count(*) FROM {table} WHERE {column} = %s", alias_id):
                raise SystemExit(f"La agencia {alias!r} (id {alias_id}) tiene filas en {table}.{column}")
        cur.execute("UPDATE terceros SET agencia_id = %s WHERE agencia_id = %s", (target_id, alias_id))
        print(f"  {alias!r} (id {alias_id}) -> {target!r} (id {target_id}): {cur.rowcount} terceros; se borra")
        moved += cur.rowcount
        cur.execute("DELETE FROM agencias WHERE id = %s", (alias_id,))

    # Despues de borrar las alias: 'Guaitarilla' era el nombre de una de ellas (UNIQUE nombre).
    for access_value, (name, active, _) in EXTRA_AGENCIES.items():
        if access_value in by_access:
            cur.execute("UPDATE agencias SET nombre = %s, activa = %s WHERE id = %s",
                        (name, active, by_access[access_value]))
            print(f"  {access_value!r} (id {by_access[access_value]}) queda como {name!r}, activa={active}")

    prefixes = count(cur, "SELECT count(*) FROM prefijos_sugeridos")
    insert_suggested_prefixes(cur, by_access)
    print(f"  Prefijos sugeridos nuevos: {count(cur, 'SELECT count(*) FROM prefijos_sugeridos') - prefixes}")

    assert count(cur, "SELECT count(*) FROM terceros") == growers, "cambio el total de terceros"
    assert not count(cur, "SELECT count(*) FROM agencias WHERE nombre_access = ANY (%s)", list(AGENCY_ALIASES)), \
        "quedo una agencia alias"
    print(f"Despues: {count(cur, 'SELECT count(*) FROM agencias')} agencias"
          f" ({count(cur, 'SELECT count(*) FROM agencias WHERE activa')} activas), {moved} terceros movidos")
    cur.execute("SELECT a.id, a.nombre, a.activa, count(t.id) FROM agencias a"
                " LEFT JOIN terceros t ON t.agencia_id = a.id GROUP BY a.id ORDER BY a.id")
    for agency_id, name, active, growers_count in cur.fetchall():
        print(f"    {agency_id:>3} {name:<32} {'activa' if active else 'INACTIVA':<8} {growers_count:>5} terceros")


def main():
    apply = "--aplicar" in sys.argv[1:]
    env = dotenv_values(BACKEND_DIR / ".env")
    db_name = os.environ.get("DB_NAME", env["DB_NAME"])
    conn = psycopg2.connect(host="localhost", port=5433, dbname=db_name, user=env["DB_USER"],
                            password=env["DB_PASSWORD"])
    try:
        with conn.cursor() as cur:
            print(f"Base {db_name} ({'APLICA' if apply else 'solo muestra; --aplicar para escribir'})")
            clean(cur)
        if apply:
            conn.commit()
            print("Aplicado.")
        else:
            conn.rollback()
            print("Sin cambios (rollback).")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
