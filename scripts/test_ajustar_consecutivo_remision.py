"""
Check de ajustar_consecutivo_remision.py contra cafeoccidente_test, todo en una transaccion que se deshace (no deja
nada): python scripts/test_ajustar_consecutivo_remision.py

Usa numeros inventados (TA0997 a TA0999, TA9999, SDXX-RP-0012, SDXX-15) sobre agencias que ya existen en la base de pruebas.
"""

from ajustar_consecutivo_remision import adjust_remission_counters, connect
from migrar_punto import set_remission_counter


def counter(cur, agency):
    cur.execute("SELECT ultimo_consecutivo FROM consecutivos_remision_agencia WHERE agencia_id = %s", (agency,))
    row = cur.fetchone()
    return row[0] if row else None


def remittance(cur, agency, number):
    cur.execute("INSERT INTO remesas (agencia_id, fondo_id, numero_remesa, cantidad_empaques, tipo_empaque, estado,"
                " creado_por_usuario_id, creado_en) SELECT %s, f.id, %s, 1, 'USADO', 'VIGENTE',"
                " (SELECT min(id) FROM usuarios), now() FROM fondos f WHERE f.codigo = 'RP'", (agency, number))


def main():
    conn = connect("cafeoccidente_test")
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM agencias ORDER BY id LIMIT 3")
            access, web, ahead = (row[0] for row in cur.fetchall())
            cur.execute("DELETE FROM consecutivos_remision_agencia WHERE agencia_id IN (%s, %s, %s)", (access, web, ahead))

            # Remesas de Access (TA0999) y de la web (SDXX-RP-0012, SDXX-15): cuenta el numero del final.
            for number in ("TA0450", "TA0997", "TA0998", "TA0999"):
                remittance(cur, access, number)
            # Un numero suelto muy arriba (como CO9999 de Consaca) no cuenta para el contador (24-nov).
            remittance(cur, access, "TA9999")
            remittance(cur, web, "SDXX-RP-0012")
            remittance(cur, web, "SDXX-15")
            # Un contador que ya va mas adelante no se baja.
            set_remission_counter(cur, ahead, 5000)

            rows = {row[0]: row for row in adjust_remission_counters(cur)}

            assert counter(cur, access) == 999, counter(cur, access)
            assert rows[access][3] is None and rows[access][4] == 999, rows[access]
            assert rows[access][5]["ignored"] == [9999], rows[access][5]
            assert counter(cur, web) == 15, counter(cur, web)
            assert counter(cur, ahead) == 5000, counter(cur, ahead)
            # Idempotente: correrlo otra vez no cambia nada.
            again = {row[0]: row for row in adjust_remission_counters(cur)}
            assert again[access][3] == again[access][4] == 999, again[access]

            # migrar_punto: el contador queda en la ultima remision de Access, nunca mas abajo de lo que ya tenia.
            set_remission_counter(cur, access, 1200)
            assert counter(cur, access) == 1200
            set_remission_counter(cur, access, 10)
            assert counter(cur, access) == 1200
        print("OK: el contador de remision sigue al mayor numero de remision/remesa de cada agencia")
    finally:
        conn.rollback()
        conn.close()


if __name__ == "__main__":
    main()
