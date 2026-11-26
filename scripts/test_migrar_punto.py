"""
Check de las reglas de migrar_punto.py que no necesitan base (sin framework):
python scripts/test_migrar_punto.py
"""

from datetime import date
from decimal import Decimal

from migrar_punto import (
    CORRECTIONS,
    DISCARDED,
    STOPS,
    apply_corrections,
    correct_rows,
    corrections_for,
    fixed_by_agency,
    fixed_remission,
    grower_type,
    note_stop,
    pending_corrections,
    split_parts,
    stop_if_problems,
    unpaid_purchase,
    announcement_number,
    cheque_text,
    control_record_differences,
    control_record_history,
    control_record_updates,
    discard_detail,
    entry_balance,
    exit_remission_rows,
    exit_remissions,
    husk_purchase,
    inventory_rows,
    keeps_purchase,
    ledger_fund,
    max_invoice,
    max_remission,
    missing_control_params,
    normal_remission_last,
    opening_purchase_id,
    out_of_range,
    payment_mismatches,
    payment_action,
    payment_of,
    pending_quotas,
    quota_purchase,
    quota_announcements,
    remission_summary,
    seller_last_name,
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
    # Una agencia con Registro de Control en la web: se listan las diferencias con Access.
    web = {"porc_aportes_asociados": Decimal("2.00"), "prefijo": "SDTA", "porc_kg_pasilla_promedio": Decimal("5.00")}
    assert control_record_differences(web, REG) == [("porc_kg_pasilla_promedio", "5.00", "6.14")]
    # 24-nov: la carga actualiza con Access los campos que Access trae; uno vacio en Access no borra el de la base.
    web = {"base_retencion": Decimal("8379840.00"), "cedula_fiel": "900000001", "prefijo": "SDTA"}
    updates = control_record_updates(web, {**REG, "baseretefte": "10000000", "fiel": "900000002", "prefijo": ""})
    assert updates == [("base_retencion", Decimal("8379840.00"), Decimal("10000000")),
                       ("cedula_fiel", "900000001", "900000002")]
    # Al Historial con los mismos nombres de campo que guarda la pantalla Registro de Control.
    assert control_record_history(updates) == ({"baseWithholding": "8379840.00", "trustedId": "900000001"},
                                               {"baseWithholding": "10000000", "trustedId": "900000002"})


def inv(factura="", remision="", cod="0110002000001", kilos="0", cantidad="0", fecha="2026-03-01 00:00:00", **extra):
    return {"factura": factura, "remisión": remision, "cod_prod": cod, "kilos_netos": kilos, "cantidad": cantidad,
            "fecha": fecha, "vr_inventario": extra.get("valor", "0"), "vr_salida": extra.get("salida", "0"),
            "porcalmsana": "90", "porcalmsanasl": "91", "valor unitario": "20000", "destino": "PRUEBA",
            "especial": "RN", "sacos": "1", "kilos_brutos": kilos}


def check_inventory():
    # 24-nov: el INVENTARIO de Access trae entradas (factura) y salidas (remision, por codigo y sin factura).
    entry, exit_row, zero = inv("1001", kilos="100", valor="2000000"), inv(remision="XX0450", cantidad="60"), inv("1002")
    entries, exits = inventory_rows([entry, exit_row, zero])
    # 25-nov: la fila en 0 kg tambien es una entrada (se carga tal cual).
    assert entries == [entry, zero] and exits == [exit_row]
    # 25-nov: una salida con "factura 0" (El Penol) se toma como sin factura y se lista en los avisos, una sola vez.
    DISCARDED.clear()
    zero_exit = inv("0", remision="XX0500", cantidad="5")
    for _ in range(2):  # la carga lee el INVENTARIO dos veces
        assert inventory_rows([zero_exit]) == ([], [zero_exit])
    assert len(DISCARDED["Salida de INVENTARIO con factura 0 (se toma como sin factura)"]) == 1
    DISCARDED.clear()
    # Saldo disponible para Registrar Salidas: los kilos solo si la compra sigue sin remesar.
    assert entry_balance(entry, {"plano": "f", "forma_de_pago": "VALIDA"}) == Decimal("100")
    assert entry_balance(entry, {"plano": "t", "forma_de_pago": "VALIDA"}) == 0
    assert entry_balance(entry, {"plano": "f", "forma_de_pago": "ANULADA"}) == 0
    # Saldo que viene del año anterior (factura 99..., sin compra): cuenta en el saldo pero no sale en una remision.
    assert entry_balance(inv("9911210226", kilos="413"), None) == 0
    assert opening_purchase_id(3, "9911210226") < 0
    assert opening_purchase_id(3, "9911210226") != opening_purchase_id(11, "9911210226")
    # Salidas: una remision por numero, con una linea por fila (TA0463 trae dos filas del mismo codigo).
    grouped = exit_remissions([inv(remision="XX0463", cantidad="10", fecha="2026-02-01 00:00:00"),
                               inv(remision="XX0463", cantidad="5", fecha="2026-02-02 00:00:00"),
                               inv(remision="XX0464", cantidad="7")])
    assert list(grouped) == [463, 464]
    assert grouped[463]["text"] == "XX0463" and grouped[463]["date"] == date(2026, 2, 2)
    assert [line["cantidad"] for line in grouped[463]["lines"]] == ["10", "5"]
    # El contador de remision tambien cuenta las salidas (una salida 1088 por encima de la remesa 1086).
    rows = [{"remision": "XX1085", "fecha_compra": "2026-10-01 00:00:00"},
            {"remision": "XX1086", "fecha_compra": "2026-10-02 00:00:00"}]
    assert max_remission(rows + exit_remission_rows([inv(remision="XX1088", cantidad="5")])) == 1088


def check_ledger_fund():
    # 25-nov: regla fija de la migracion, nada se supone. Un fondo que no es RP ni LF no tiene fondo (None) y
    # detiene la carga; solo correcciones.csv puede excluir ese registro.
    assert ledger_fund({"id_transaccion": "SUMPRUEBA", "fondo": "LF"}) == "LF"
    assert ledger_fund({"id_transaccion": "SUMPRUEBA", "fondo": "RP"}) == "RP"
    assert ledger_fund({"id_transaccion": "sumlf60-29-05", "fondo": "j"}) is None
    assert ledger_fund({"id_transaccion": "280", "fondo": "RTP"}) is None
    row = {"id_access": "8", "fecha": "2026-03-18 00:00:00", "id_transaccion": "02", "detalle": "SUM PRUEBA",
           "fondo": "02"}
    assert discard_detail(row, Decimal("1500000"), Decimal("0")) == (
        "Id 8, 2026-03-18, 02, SUM PRUEBA, fondo 02, entradas 1500000, salidas 0")


def correction(**extra):
    row = {"punto": "Punto Prueba", "tabla": "Suministros", "id_access": "8", "accion": "excluir",
           "motivo": "posible duplicado del Id 10", "confirmado_por": "PENDIENTE"}
    row.update(extra)
    return row


def check_corrections():
    # 25-nov: correcciones.csv (fuera del repo) es lo unico que puede excluir un registro de Access.
    found = corrections_for([correction(), correction(punto="Otro", id_access="9")], "PuntoPrueba")
    assert list(found) == [("suministros", "8")]
    assert pending_corrections(found) == [("suministros", "8")]
    assert pending_corrections(corrections_for([correction(confirmado_por="Jefe de area")], "puntoprueba")) == []
    for bad in (correction(accion="corregir"), correction(motivo=""), correction(tabla="Nada"),
                correction(id_access="x")):
        try:
            corrections_for([bad], "PuntoPrueba")
        except SystemExit:
            continue
        raise AssertionError(bad)
    rows = [{"id_access": "7", "fondo": "RP"}, {"id_access": "8", "fondo": "02"}]
    kept, excluded = apply_corrections("suministros_migrar.csv", rows, found)
    assert kept == [rows[0]] and excluded == [rows[1]]
    assert apply_corrections("caja_migrar.csv", rows, found) == (rows, [])


def check_correction_actions():
    # 25-nov: corregir un campo de un registro (Matituy Id 702: remision "MA0586," -> "MA0586") y fijar el contador
    # de remision del punto (Samaniego: la ultima es 1934, la siguiente SA1935).
    fix = correction(tabla="INVENTARIO", id_access="702", accion="corregir", campo="Remisión", valor="MA0586",
                     motivo="misma remision MA0586 que el Id 693")
    counter = correction(tabla="", id_access="", accion="fijar_remision", valor="1934", motivo="serie normal")
    found = corrections_for([fix, counter], "PuntoPrueba")
    assert set(found) == {("inventario", "702"), ("consecutivo_remision", "")}
    assert fixed_remission(found) == 1934 and fixed_remission({}) is None
    rows = [{"id_access": "701", "remisión": "MA0585"}, {"id_access": "702", "remisión": "MA0586,"}]
    changes = correct_rows("inventario_migrar.csv", rows, found)
    assert rows[1]["remisión"] == "MA0586" and rows[0]["remisión"] == "MA0585"
    assert changes == [(("inventario", "702"), "remisión", "MA0586,", "MA0586")]
    try:
        correct_rows("inventario_migrar.csv", [{"id_access": "702", "cantidad": "1"}], found)
    except SystemExit:
        pass
    else:
        raise AssertionError("un campo que no existe debe detener la carga")
    for bad in (correction(accion="corregir", campo="", valor="x"), correction(accion="fijar_remision", valor="x")):
        try:
            corrections_for([bad], "PuntoPrueba")
        except SystemExit:
            continue
        raise AssertionError(bad)
    # El contador fijo se reconoce por agencia (nombre o nombre de Access), tambien para ajustar_consecutivo_remision.
    agencies = [(9, "Samaniego", "SAMANIEGO"), (11, "Los Andes (Sotomayor)", "SOTOMAYOR")]
    assert fixed_by_agency([dict(counter, punto="Samaniego")], agencies) == {9: 1934}
    assert fixed_by_agency([dict(counter, punto="Sotomayor")], agencies) == {11: 1934}
    assert fixed_by_agency([correction()], agencies) == {}


def check_growers_by_id_number():
    # 25-nov: Asociados no tiene Id en Access; en correcciones.csv se senala por la cedula (IdAsociado).
    fix = correction(punto="PuntoPrueba", tabla="Asociados", id_access="5285436", accion="corregir", campo="Tipo",
                     valor="C", motivo="el area dice el tipo")
    found = corrections_for([fix], "PuntoPrueba")
    rows = [{"idasociado": "5285436", "tipo": ""}, {"idasociado": "1", "tipo": "F"}]
    assert correct_rows("asociados_migrar.csv", rows, found) == [(("asociados", "5285436"), "tipo", "", "C")]
    assert rows[0]["tipo"] == "C" and rows[1]["tipo"] == "F"
    gone = corrections_for([dict(fix, accion="excluir")], "PuntoPrueba")
    assert apply_corrections("asociados_migrar.csv", rows, gone) == ([rows[1]], [rows[0]])


def check_payments():
    # 25-nov: lo pagado en Caja (salidas - entradas) por cada compra de Access debe ser su neto, y lo de Suministros
    # su parte en cheque. Si no cuadra (un pago registrado dos veces, o ninguno) la carga se detiene.
    compra = {"id_access": "7", "factura": "1001", "fecha_compra": "2026-03-01 08:00:00", "neto_a_pagar": "300",
              "fpch": "200", "forma_de_pago": "VALIDA"}
    cash = [{"id_access": "11", "id_transaccion": "1001", "fecha": "2026-03-01 08:00:00", "entradas": "0",
             "salidas": "100"},
            {"id_access": "12", "id_transaccion": "1001", "fecha": "2026-03-01 08:00:00", "entradas": "0",
             "salidas": "200"}]
    supply = [{"id_access": "21", "id_transaccion": "1001", "fecha": "2026-03-01 08:00:00", "valor_suministro": "0",
               "vr_gastos_o_comp": "200"}]
    assert payment_mismatches([compra], cash, supply) == []
    double = cash + [dict(cash[0], id_access="13")]
    found = payment_mismatches([compra], double, supply)
    assert len(found) == 1 and "factura 1001" in found[0] and "Caja 400" in found[0] and "Id 13" in found[0]
    unpaid = dict(compra, factura="1002", neto_a_pagar="50", fpch="0")
    assert "Caja 0" in payment_mismatches([unpaid], [], [])[0]
    assert payment_mismatches([dict(unpaid, neto_a_pagar="0", forma_de_pago="ANULADA")], [], []) == []
    # 25-nov (opcion A): una compra valida sin pago en Access se carga tal cual si correcciones.csv la marca
    # "sin_pago"; la revision la acepta solo si de verdad no tiene ningun pago.
    assert payment_mismatches([unpaid], [], [], unpaid={"1002"}) == []
    partial = [dict(cash[0], id_transaccion="1002", salidas="10")]
    assert len(payment_mismatches([unpaid], partial, [], unpaid={"1002"})) == 1
    found = corrections_for([correction(tabla="COMPRAS", id_access="7", accion="sin_pago",
                                        motivo="compra valida sin pago en Access")], "PuntoPrueba")
    assert found == {("compras", "7"): found[("compras", "7")]}
    CORRECTIONS.clear()
    CORRECTIONS.update(found)
    try:
        assert unpaid_purchase({"id_access": "7", "fpch": "0"}) and not unpaid_purchase({"id_access": "8"})
        assert payment_of({"id_access": "7", "fpch": "0"}) == ("SIN PAGO", None)
        assert payment_of({"id_access": "8", "fpch": "0"}) == ("EFECTIVO", None)
    finally:
        CORRECTIONS.clear()


def check_payment_corrections():
    # 26-nov: pago_efectivo = compra que el area da por pagada en efectivo aunque Access no tiene el pago en Caja;
    # pago_tal_cual = el pago de Access no cuadra con el neto y se carga como esta. Solo si estan en correcciones.csv.
    compra = {"id_access": "7", "factura": "1003", "fecha_compra": "2026-03-01 08:00:00", "neto_a_pagar": "300",
              "fpef": "0", "fpch": "0", "fptx": "0", "fpdat": "0", "forma_de_pago": "VALIDA"}
    assert len(payment_mismatches([compra], [], [])) == 1
    assert payment_mismatches([compra], [], [], cash_paid={"1003"}) == []
    paid_some = [{"id_access": "11", "id_transaccion": "1003", "fecha": "2026-03-01", "entradas": "0", "salidas": "10"}]
    assert len(payment_mismatches([compra], paid_some, [], cash_paid={"1003"})) == 1  # si tiene algun pago, no
    over = [dict(paid_some[0], salidas="300"), dict(paid_some[0], id_access="12", salidas="5")]
    assert len(payment_mismatches([compra], over, [])) == 1
    assert payment_mismatches([compra], over, [], as_is={"1003"}) == []
    for action in ("pago_efectivo", "pago_tal_cual"):
        found = corrections_for([correction(tabla="COMPRAS", id_access="7", accion=action, motivo="decision del area")],
                                "PuntoPrueba")
        CORRECTIONS.clear()
        CORRECTIONS.update(found)
        try:
            assert payment_action({"id_access": "7"}) == action and payment_action({"id_access": "8"}) is None
            assert payment_of({"id_access": "7", "fpch": "0"}) == ("EFECTIVO", None)
        finally:
            CORRECTIONS.clear()
    # El desglose que no suma el neto se guarda tal cual solo con pago_tal_cual.
    assert split_parts({"fpef": "5", "fptx": "300", "fpch": "0", "fpdat": "0"}, Decimal("300"), "pago_tal_cual") ==         [Decimal("5"), Decimal("0"), Decimal("300"), Decimal("0")]
    assert split_parts({"fpef": "5", "fptx": "300", "fpch": "0", "fpdat": "0"}, Decimal("300"), None) is None


def check_grower_type():
    # 25-nov: el tipo de asociado vacio no se inventa ("C"): detiene la carga (None) y se corrige con correcciones.csv.
    assert grower_type({"tipo": "c"}) == "C" and grower_type({"tipo": "F"}) == "F"
    assert grower_type({"tipo": ""}) is None and grower_type({}) is None


def check_stops():
    # 25-nov: lo que no se puede cargar tal cual no se descarta: se junta y la carga se detiene al final.
    STOPS.clear()
    note_stop("Suministros con Fondo inexistente", "Id 8, ...")
    note_stop("Suministros con Fondo inexistente", "Id 8, ...")  # el mismo registro leido dos veces se lista una vez
    assert STOPS == {"Suministros con Fondo inexistente": ["Id 8, ..."]}
    try:
        stop_if_problems()
    except SystemExit:
        STOPS.clear()
    else:
        raise AssertionError("la carga debia detenerse")
    stop_if_problems()  # sin problemas no se detiene


def check_seller_without_last_name():
    # 24-nov: un caficultor sin apellido en Access (nombre completo en el primer nombre) no detiene la carga.
    assert seller_last_name(None) == ""
    assert seller_last_name("  ") == ""
    assert seller_last_name("PRUEBA") == "PRUEBA"


def check_date_cut():
    desde = date(2026, 6, 1)
    assert keeps_purchase(purchase("1", "2026-06-01 08:00:00"), desde)
    # Antes del corte solo entra si sigue sin remesar (Plano = f), para que Genera Remesa cuadre con Access.
    assert not keeps_purchase(purchase("2", "2026-05-31 23:59:59"), desde)
    assert keeps_purchase(purchase("3", "2026-01-02 00:00:00", plano="f"), desde)
    assert not keeps_purchase(purchase("4", "2026-01-02 00:00:00", plano="f", estado="ANULADA"), desde)
    assert keeps_purchase(purchase("5", "2020-01-01 00:00:00"), None)


def check_cheque_text():
    # 25-nov: el cheque de Access se carga tal cual, sin rellenar con ceros (la base acepta de 1 a 15 digitos, V34).
    assert cheque_text("548") == "548"
    assert cheque_text("52") == "52"
    assert cheque_text("13324") == "13324"
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
    # Un numero suelto arriba (CO1341 -> CO9999) no cuenta para el contador; la remesa conserva su numero (24-nov).
    assert max_remission([{"remision": "XX1339"}, {"remision": "XX1341"}, {"remision": "XX9999"}]) == 1341
    assert normal_remission_last([1339, 1340, 1341, 9999]) == (1341, [9999])
    # Si la numeracion normal siguio despues del salto, el contador sigue con ella.
    assert normal_remission_last([1341, 9999, 1342]) == (1342, [9999])
    # Dos series reales en el mismo punto (una baja de RP y la alta de siempre): se respeta la alta.
    assert normal_remission_last([436, 441, 475, 941, 945, 994, 997]) == (997, [])
    # Saltos pequenos son normales (numeros que no se usaron).
    assert normal_remission_last([436, 441, 470, 540]) == (540, [])
    assert normal_remission_last([]) == (0, [])
    assert normal_remission_last([9999]) == (9999, [])
    # Ultima por numero y por fecha, y las ignoradas, para mostrarlas antes de aplicar.
    summary = remission_summary([
        purchase("1", fecha="2026-08-20 08:00:00") | {"remision": "XX1341"},
        purchase("2", fecha="2026-08-25 08:00:00") | {"remision": "XX9999"},
        purchase("3", fecha="2026-08-21 08:00:00") | {"remision": "XX1340"},
        purchase("4", fecha="2026-08-22 08:00:00") | {"remision": "XX1340"},
    ])
    assert summary == {"by_number": ("XX1341", date(2026, 8, 20)), "by_date": ("XX1340", date(2026, 8, 22)),
                       "ignored": [("XX9999", 1, date(2026, 8, 25), date(2026, 8, 25))], "next": 1342}
    # Id de transaccion: Access los digitaba libres; ninguno puede chocar con PREFIJO + codigo + 3 cifras.
    assert transaction_collisions(["43000", "che1234", "sumlf60-29-05", "TA0012"], "SDTA") == []
    assert transaction_collisions(["SDTA3SUM001", "SDTA2002", "sdta7GAS010"], "SDTA") == [
        "SDTA3SUM001", "SDTA2002", "sdta7GAS010"]
    # Formato con guiones (prueba del 9-nov): SDTA-3SUM-001 tambien choca; un Id de Access con guiones libres no.
    assert transaction_collisions(["SDTA-3SUM-001", "SDTA-2-002", "SDTA-ALMACEN-12"], "SDTA") == [
        "SDTA-3SUM-001", "SDTA-2-002"]


def main():
    check_control_record()
    check_seller_without_last_name()
    check_ledger_fund()
    check_corrections()
    check_stops()
    check_correction_actions()
    check_growers_by_id_number()
    check_grower_type()
    check_payments()
    check_payment_corrections()
    check_inventory()
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
