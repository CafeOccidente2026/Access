"""
Una vez, antes de V11 (fase 5a): los anuncios cargados de Access traen la bonificacion por kilo en la
columna obsoleta `bonificacion` y dejan vacia `sobrepr_carga_esp`, que es la que usan las compras
(Bonificacion = SobrePr_CPS / BaseCarga, Form_ANUNCIOS CORRF.bas). Este script la completa con
SobrePr = bonificacion x Base Carga del Registro de Control activo de la agencia del anuncio.

    python scripts/completar_sobrepr_anuncios.py            # solo muestra lo que haria
    python scripts/completar_sobrepr_anuncios.py --aplicar  # escribe

DB_NAME del entorno elige la base, como en migrate_eltambo.py.
"""

import os
import sys

import psycopg2
from dotenv import dotenv_values

from migrate_eltambo import BACKEND_DIR

PENDING = (
    "FROM anuncios a JOIN registros_control r ON r.agencia_id = a.agencia_id AND r.activo"
    " WHERE a.sobrepr_carga_esp IS NULL AND a.bonificacion > 0"
)


def main():
    apply = "--aplicar" in sys.argv[1:]
    env = dotenv_values(BACKEND_DIR / ".env")
    db_name = os.environ.get("DB_NAME", env["DB_NAME"])
    conn = psycopg2.connect(host="localhost", port=5433, dbname=db_name, user=env["DB_USER"],
                            password=env["DB_PASSWORD"])
    try:
        with conn.cursor() as cur:
            print(f"Base {db_name} ({'APLICA' if apply else 'solo muestra; --aplicar para escribir'})")
            cur.execute("SELECT a.especial, a.bonificacion, r.base_carga, a.bonificacion * r.base_carga, count(*) "
                        + PENDING + " GROUP BY 1, 2, 3 ORDER BY 1, 2")
            for special, per_kilo, base_load, surcharge, count in cur.fetchall():
                print(f"  {special:<42} {per_kilo:>8} x {base_load} = {surcharge:>12}  ({count} anuncios)")
            cur.execute("UPDATE anuncios a SET sobrepr_carga_esp = a.bonificacion * r.base_carga"
                        " FROM registros_control r WHERE r.agencia_id = a.agencia_id AND r.activo"
                        " AND a.sobrepr_carga_esp IS NULL AND a.bonificacion > 0")
            print(f"Anuncios completados: {cur.rowcount}")
            cur.execute("SELECT count(*) FROM anuncios WHERE sobrepr_carga_esp IS NULL AND bonificacion > 0")
            print(f"Quedan con bonificacion y sin SobrePr (agencia sin Registro de Control): {cur.fetchone()[0]}")
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
