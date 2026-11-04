"""
Check del armado de remesas de cargar_remesas_access.py (sin BD ni framework):
python scripts/test_cargar_remesas_access.py
"""

from cargar_remesas_access import plan_remittances


def row(factura, remision, plano="t", fondo="RP", fecha="2026-01-17 00:00:00", estado="VALIDA", kilos="30.0"):
    return {"factura": factura, "remision": remision, "plano": plano, "fondo": fondo, "fecha_compra": fecha,
            "forma_de_pago": estado, "kilos_netos": kilos}


def main():
    rows = [
        row("43001", "TA0436", fecha="2026-01-17 00:00:00"),
        row("43000", "TA0436", fecha="2026-01-24 08:30:00"),
        row("43010", "TA0437", fondo="LF", fecha="2026-02-07 00:00:00"),
        row("43020", "", plano="f"),  # pendiente: no entra
        row("43021", "", plano="f", estado="ANULADA", kilos="0"),
    ]
    plan, pending = plan_remittances(rows)

    # Una remesa por numero, con su fondo, la fecha de su ultima compra y sus facturas en orden.
    assert list(plan) == ["TA0436", "TA0437"], plan
    assert plan["TA0436"] == {"fondo": "RP", "fecha": "2026-01-24 08:30:00", "facturas": [43000, 43001]}
    assert plan["TA0437"] == {"fondo": "LF", "fecha": "2026-02-07 00:00:00", "facturas": [43010]}
    assert pending == [43020, 43021]

    # Errores que deben detener la carga.
    for bad, expected in [
        ([row("1", "TA1", fondo="RP"), row("2", "TA1", fondo="LF")], "mezcla fondos"),
        ([row("1", "", plano="t")], "sin numero"),
        ([row("1", "TA1", estado="ANULADA")], "anulada"),
        ([row("1", "TA1", kilos="0")], "sin kilos"),
        ([row("1", "TA1"), row("1", "TA2")], "repetida"),
    ]:
        try:
            plan_remittances(bad)
        except ValueError as error:
            assert expected in str(error), (expected, error)
        else:
            raise AssertionError(f"no detecto: {expected}")
    print("OK")


if __name__ == "__main__":
    main()
