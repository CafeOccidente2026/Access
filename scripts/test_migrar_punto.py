"""
Check de las reglas de migrar_punto.py que no necesitan base (sin framework):
python scripts/test_migrar_punto.py
"""

from datetime import date
from decimal import Decimal

from migrar_punto import (
    announcement_number,
    cheque_text,
    control_record_differences,
    husk_purchase,
    keeps_purchase,
    max_invoice,
    max_remission,
    missing_control_params,
    out_of_range,
    pending_quotas,
    quota_purchase,
    quota_announcements,
    split_future_purchases,
    transaction_collisions,
)

REG = {
    "numregistro": "2", "factorbase": "94.0", "baseretefte": "8379840", "basecarga": "125", "porcretefte": "0.5",
    "basepasilla": "12.5", "porckgpasprom": "6.14", "agencia": "EL TAMBO", "prefijo": "SDTA", "costos": "698",
    "muestra": "250.0", "kgexcelso": "70", "porcverde": "50.0", "umbralespeciales": "93.33", "porcdesccoop": "0.8",
    "porcasociados": "2.0", "fiel": "99304051", "resdian": "18764112142534", "fecharesdian": "2026-07-04 00:00:00",
    "desderes": "43754", "hastares": "48753", "vigenciares": "24",
}


def purchase(factura, fecha="2026-03-01 00:00:00", plano="t", especial="RN", estado="VALIDA"):
    return {"factura": factura, "fecha_compra": fecha, "plano": plano, "especial": especial, "forma_de_pago": estado}


def check_control_record():
    assert missing_control_params(REG) == []
    # Decision del usuario: un parametro vacio detiene la carga; nunca se pone 0.
    assert missing_control_params({**REG, "porcasociados": "", "muestra": " "}) == ["muestra", "porcasociados"]
    # Una agencia con Registro de Control en la web no se toca: solo se listan las diferencias.
    web = {"porc_aportes_asociados": Decimal("2.00"), "prefijo": "SDTA", "porc_kg_pasilla_promedio": Decimal("5.00")}
    assert control_record_differences(web, REG) == [("porc_kg_pasilla_promedio", "5.00", "6.14")]


def check_date_cut():
    desde = date(2026, 6, 1)
    assert keeps_purchase(purchase("1", "2026-06-01 08:00:00"), desde)
    # Antes del corte solo entra si sigue sin remesar (Plano = f), para que Genera Remesa cuadre con Access.
    assert not keeps_purchase(purchase("2", "2026-05-31 23:59:59"), desde)
    assert keeps_purchase(purchase("3", "2026-01-02 00:00:00", plano="f"), desde)
    assert not keeps_purchase(purchase("4", "2026-01-02 00:00:00", plano="f", estado="ANULADA"), desde)
    assert keeps_purchase(purchase("5", "2020-01-01 00:00:00"), None)


def check_cheque_text():
    # V21: el cheque son los ultimos 4 digitos con sus ceros (0548). Asi se guarda y asi se reconoce lo ya cargado.
    assert cheque_text("548") == "0548"
    assert cheque_text("5249.0") == "5249"
    assert cheque_text("0") is None and cheque_text("") is None and cheque_text(None) is None


def check_husk():
    assert husk_purchase(purchase("27966", especial="PASILLA"))
    assert not husk_purchase(purchase("27967", especial="RN"))


def check_out_of_range():
    # Factura 27959 de Buesaco: Peso Tot Alm digitado 205000 -> Porc Merma -81.900: no cabe y no se inventa otro valor.
    row = {"porcmerma": "-81900.0", "porcalmdefec": "2.0", "porcalmsana": "87.5", "especial": "RN"}
    assert out_of_range(row) == ["porcmerma"]
    assert out_of_range({**row, "porcmerma": "9999.99"}) == []
    # En la pasilla solo cuenta el % de almendra.
    assert out_of_range({"porcmerma": "-81900.0", "porcalmsana": "50.0", "especial": "PASILLA"}) == []


def check_future_purchases():
    rows = [
        {"anuncio": "1", "saldo_kilos": "293.0", "fecha_entrega": "2021-08-31 00:00:00"},
        {"anuncio": "2", "saldo_kilos": "50.0", "fecha_entrega": "2026-11-09 00:00:00"},
        {"anuncio": "3", "saldo_kilos": "0.0", "fecha_entrega": "2027-01-01 00:00:00"},
    ]
    load, expired = split_future_purchases(rows, date(2026, 11, 9))
    # Decision del usuario: solo las que tienen saldo y no estan vencidas; las vencidas se listan.
    assert [r["anuncio"] for r in load] == ["2"]
    assert [r["anuncio"] for r in expired] == ["1"]


def check_quotas():
    rows = [{"anuncio": "3458", "cupo": "6000.0", "saldo": "5999.0"}, {"anuncio": "3459", "cupo": "0.0", "saldo": "0"}]
    assert [r["anuncio"] for r in quota_announcements(rows)] == ["3458"]


def check_announcement_number():
    # Decision (a) del 18-nov: como la web (AnnouncementServiceImpl), el anuncio va con el prefijo del punto.
    assert announcement_number("SDTA", "3490") == "SDTA-3490"
    assert announcement_number("SDTA", " 3490.0 ") == "SDTA-3490"
    # Ya con prefijo (una segunda corrida) o vacio: se deja como esta.
    assert announcement_number("SDTA", "SDTA-3490") == "SDTA-3490"
    assert announcement_number("SDTA", "") == ""


def check_pending_quotas():
    # Decision (b) del 18-nov: solo los cupos con saldo el dia del corte = cupo - kilos netos de las compras validas.
    announcements = [
        {"anuncio": "9001", "cupo": "6000.0", "entregados": "1.0"},     # Entregados de Access viejo: no se usa.
        {"anuncio": "9002", "cupo": "26442.0", "entregados": "0"},
        {"anuncio": "9003", "cupo": "0.0", "entregados": "0"},          # sin cupo: no es cupo de anuncio.
        {"anuncio": "9004", "cupo": "500.0", "entregados": "0"},        # sin compras: todo el cupo pendiente.
    ]
    purchases = [
        {"anuncio": "9001", "kilos_netos": "6000.0", "forma_de_pago": "VALIDA"},
        {"anuncio": "9001", "kilos_netos": "300.0", "forma_de_pago": "VALIDA"},
        {"anuncio": "9002", "kilos_netos": "20030.0", "forma_de_pago": "VALIDA"},
        {"anuncio": "9002", "kilos_netos": "900.0", "forma_de_pago": "ANULADA"},  # anulada: no cuenta.
        {"anuncio": "9003", "kilos_netos": "50.0", "forma_de_pago": "VALIDA"},
    ]
    pending = pending_quotas(announcements, purchases)
    assert [(r["anuncio"], saldo) for r, saldo in pending] == [("9002", Decimal("6412.0")), ("9004", Decimal("500.0"))]


def check_quota_purchases_kept_before_the_cut():
    # Las compras de un cupo cargado entran aunque sean de antes del corte: la web suma Entregados con ellas.
    desde = date(2026, 9, 1)
    old = {**purchase("7", "2026-07-15 00:00:00"), "anuncio": "9002"}
    assert not keeps_purchase(old, desde)
    assert keeps_purchase(old, desde, {"9002"})
    assert not keeps_purchase({**old, "anuncio": "9001"}, desde, {"9002"})


def check_quota_purchase():
    # Regla del 18-nov: el saldo del cupo solo baja con las compras de cupo. Access las guardaba en COMPRAS sin marca,
    # asi que las validas de un anuncio cuyo cupo se carga quedan como compras de cupo (con_cupo), igual que
    # pending_quotas; las de otros anuncios y las anuladas no.
    kept = {"9002"}
    assert quota_purchase({"anuncio": "9002", "forma_de_pago": "EFECTIVO"}, kept)
    assert not quota_purchase({"anuncio": "9002", "forma_de_pago": "ANULADA"}, kept)
    assert not quota_purchase({"anuncio": "9001", "forma_de_pago": "EFECTIVO"}, kept)
    assert not quota_purchase({"anuncio": "", "forma_de_pago": "EFECTIVO"}, kept)


def check_consecutives():
    # Factura: la mayor de COMPRAS, tambien las anuladas.
    assert max_invoice([purchase("43902"), purchase("43903", estado="ANULADA"), purchase("43001")]) == 43903
    # Remision: TA0968 -> 968; sin remisiones, 0.
    assert max_remission([{"remision": "TA0942"}, {"remision": "TA0968"}, {"remision": ""}]) == 968
    assert max_remission([{"remision": ""}]) == 0
    # Id de transaccion: Access los digitaba libres; ninguno puede chocar con PREFIJO + codigo + 3 cifras.
    assert transaction_collisions(["43000", "che1234", "sumlf60-29-05", "TA0012"], "SDTA") == []
    assert transaction_collisions(["SDTA3SUM001", "SDTA2002", "sdta7GAS010"], "SDTA") == [
        "SDTA3SUM001", "SDTA2002", "sdta7GAS010"]
    # Formato con guiones (prueba del 9-nov): SDTA-3SUM-001 tambien choca; un Id de Access con guiones libres no.
    assert transaction_collisions(["SDTA-3SUM-001", "SDTA-2-002", "SDTA-ALMACEN-12"], "SDTA") == [
        "SDTA-3SUM-001", "SDTA-2-002"]


def main():
    check_control_record()
    check_date_cut()
    check_cheque_text()
    check_husk()
    check_out_of_range()
    check_future_purchases()
    check_quotas()
    check_announcement_number()
    check_pending_quotas()
    check_quota_purchases_kept_before_the_cut()
    check_quota_purchase()
    check_consecutives()
    print("OK")


if __name__ == "__main__":
    main()
