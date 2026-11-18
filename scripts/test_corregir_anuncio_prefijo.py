"""
Check de corregir_anuncio_prefijo.py contra cafeoccidente_test, todo en una transaccion que se deshace (no deja nada):
python scripts/test_corregir_anuncio_prefijo.py

Usa numeros de anuncio inventados (987654 y SDXX-5) sobre filas que ya existen en la base de pruebas.
"""

from corregir_anuncio_prefijo import TABLES, connect, fix_announcement_prefixes


def one_row(cur, table):
    """Una fila de la tabla cuya agencia tiene Registro de Control activo, con su prefijo; None si no hay."""
    cur.execute(f"SELECT t.id, r.prefijo FROM {table} t JOIN registros_control r"
                " ON r.agencia_id = t.agencia_id AND r.activo ORDER BY t.id LIMIT 2")
    return cur.fetchall()


def main():
    conn = connect("cafeoccidente_test")
    try:
        with conn.cursor() as cur:
            checked = 0
            for table in TABLES:
                rows = one_row(cur, table)
                if not rows:
                    continue
                (bare_id, prefix), *rest = rows
                cur.execute(f"UPDATE {table} SET numero_anuncio = '987654' WHERE id = %s", (bare_id,))
                if rest:
                    cur.execute(f"UPDATE {table} SET numero_anuncio = 'SDXX-5' WHERE id = %s", (rest[0][0],))

                fix_announcement_prefixes(cur)

                cur.execute(f"SELECT numero_anuncio FROM {table} WHERE id = %s", (bare_id,))
                assert cur.fetchone()[0] == f"{prefix}-987654", table
                if rest:
                    # Ya con prefijo: no se toca (idempotente).
                    cur.execute(f"SELECT numero_anuncio FROM {table} WHERE id = %s", (rest[0][0],))
                    assert cur.fetchone()[0] == "SDXX-5", table
                checked += 1
            # Una segunda corrida no cambia nada mas.
            assert sum(fix_announcement_prefixes(cur).values()) == 0
            assert checked > 0, "la base de pruebas no tiene compras"
    finally:
        conn.rollback()
        conn.close()
    print(f"OK ({checked} tablas)")


if __name__ == "__main__":
    main()
