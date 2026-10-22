"""
Carga de datos reales de El Tambo (Access -> Postgres).

Fuente de verdad: docs/legacy-postgres-reference/eltambo/*.csv +
docs/legacy-postgres-reference/PosgreSQL/*.sql (estructura original).

Parte A: agencias, registros_control, terceros, anuncios (+ anuncios_agencias),
compras_cafe_seco, caja, suministros, caja menor, empaques y formas de pago
(tablas de Flyway, esquema en español). Idempotente: upsert por llave natural,
no duplica si se vuelve a correr. Crea admin_eltambo (ADMIN, El Tambo) si no
existe, con una contrasena aleatoria que no se guarda ni se imprime.

Parte B: cupos_ness (tabla NESS de Access). Se vacia y se recarga en cada corrida.

Dependencias: scripts/requirements.txt.
"""

import csv
import os
import re
import secrets
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

import bcrypt
import psycopg2
import psycopg2.extras
from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = ROOT / "cafeoccidente-backend"
DATA_DIR = ROOT / "docs" / "legacy-postgres-reference" / "eltambo"

DISCARDED = {}


def note_discard(reason, detail):
    DISCARDED.setdefault(reason, []).append(detail)


# ---------- helpers ----------

def read_csv(name):
    with open(DATA_DIR / name, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def s(row, key, default=""):
    return (row.get(key) or "").strip() or default


def parse_date(value):
    value = (value or "").strip()
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(value[:19], fmt).date()
        except ValueError:
            continue
    return None


def parse_timestamp(value):
    value = (value or "").strip()
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(value[:19], fmt)
        except ValueError:
            continue
    return None


def parse_decimal(value, default="0"):
    value = (value or "").strip()
    if not value:
        value = default
    try:
        return Decimal(value)
    except InvalidOperation:
        return Decimal(default)


def parse_int(value, default=0):
    value = (value or "").strip()
    if not value:
        return default
    try:
        return int(round(float(value)))
    except ValueError:
        return default


def parse_bool(value):
    return (value or "").strip().lower() == "t"


# ---------- Parte A ----------

def load_agency_el_tambo(cur):
    reg = read_csv("regcontrol_migrar.csv")[0]
    access_value = s(reg, "agencia")
    prefix = s(reg, "prefijo")
    cur.execute("SELECT id, prefijo_factura FROM agencias WHERE nombre_access = %s", (access_value,))
    row = cur.fetchone()
    if row:
        agency_id, current_prefix = row
        if current_prefix != prefix:
            cur.execute("UPDATE agencias SET prefijo_factura = %s WHERE id = %s", (prefix, agency_id))
        return agency_id
    cur.execute(
        "INSERT INTO agencias (nombre, nombre_access, prefijo_factura, activa) "
        "VALUES (%s, %s, %s, true) RETURNING id",
        ("El Tambo", access_value, prefix),
    )
    return cur.fetchone()[0]


def load_control_record(cur, el_tambo_id):
    reg = read_csv("regcontrol_migrar.csv")[0]
    purchase_point = s(reg, "agencia")
    prefix = s(reg, "prefijo")
    cur.execute(
        "SELECT id FROM registros_control WHERE punto_compra = %s AND prefijo = %s",
        (purchase_point, prefix),
    )
    if cur.fetchone():
        print("  registros_control de El Tambo ya existe, se omite.")
        return

    cur.execute(
        """
        INSERT INTO registros_control (
            agencia_id, activo, numero_registro_control, kg_excelso, base_retencion, base_carga,
            porcentaje_retencion, base_pasilla, porc_kg_pasilla_promedio, punto_compra,
            prefijo, costos, tamano_muestra, kg_excelso_copia, porcentaje_cafe_verde,
            factor_base, porc_aportes_asociados, porc_desc_coop_no_asociados,
            cedula_fiel, resolucion_dian, fecha_resolucion, desde_resolucion,
            hasta_resolucion, vigencia
        ) VALUES (
            %s, true, %s, %s, %s, %s,
            %s, %s, %s, %s,
            %s, %s, %s, %s, %s,
            %s, %s, %s,
            %s, %s, %s, %s,
            %s, %s
        )
        """,
        (
            el_tambo_id,
            parse_int(reg["numregistro"]),
            # NOTE: nombres de columnas del CSV estan cruzados respecto al orden de
            # los campos en ControlRecord - validado contra compras_migrar.csv real
            # (columna kg_excelso = 70 = kgexcelso, campo Java baseFactor;
            # columna factor_base = 94 = factorbase, campo Java specialtyThreshold).
            parse_int(reg["kgexcelso"]),  # -> kg_excelso
            parse_decimal(reg["baseretefte"]),
            parse_int(reg["basecarga"]),
            parse_decimal(reg["porcretefte"]),
            parse_decimal(reg["basepasilla"]),
            parse_decimal(reg["porckgpasprom"]),
            purchase_point,
            prefix,
            parse_decimal(reg["costos"]),
            parse_decimal(reg["muestra"]),
            # kg_excelso_copia no se usa en ningun calculo (ver DryCoffeePurchaseCalculator);
            # se deja igual a kgexcelso ya que no hay evidencia de su fuente real correcta.
            parse_decimal(reg["kgexcelso"]),
            parse_decimal(reg["porcverde"]),
            parse_decimal(reg["factorbase"]),  # -> factor_base
            # NOTE: nombres de columnas del CSV estan cruzados respecto al orden de
            # los campos en ControlRecord - validado contra compras_migrar.csv real
            # (descuento_coop observado = 0.8% = porcdesccoop, no porcasociados).
            parse_decimal(reg["porcasociados"]),  # -> porc_aportes_asociados
            parse_decimal(reg["porcdesccoop"]),  # -> porc_desc_coop_no_asociados
            s(reg, "fiel"),
            s(reg, "resdian"),
            parse_date(reg["fecharesdian"]),
            parse_int(reg["desderes"]),
            parse_int(reg["hastares"]),
            parse_int(reg["vigenciares"]),
        ),
    )
    print("  registros_control de El Tambo insertado.")


def resolve_agencies(cur, raw_values):
    """Resuelve/crea una Agency por cada valor crudo distinto de 'agencia' en asociados_migrar.csv."""
    cur.execute("SELECT id, nombre, nombre_access FROM agencias")
    by_access = {}
    by_name = {}
    for agency_id, name, access_value in cur.fetchall():
        by_access[access_value] = agency_id
        by_name[name] = agency_id

    value_to_id = {}
    created = []
    for value in sorted(raw_values):
        if value in by_access:
            value_to_id[value] = by_access[value]
        elif value in by_name:
            value_to_id[value] = by_name[value]
        else:
            cur.execute(
                "INSERT INTO agencias (nombre, nombre_access, prefijo_factura, activa) "
                "VALUES (%s, %s, '', true) RETURNING id",
                (value, value),
            )
            new_id = cur.fetchone()[0]
            by_access[value] = new_id
            by_name[value] = new_id
            value_to_id[value] = new_id
            created.append(value)
    return value_to_id, created


def load_growers(cur):
    rows = read_csv("asociados_migrar.csv")
    raw_agencias = {s(row, "agencia") for row in rows} - {"", "0"}
    agency_map, created = resolve_agencies(cur, raw_agencias)
    if created:
        print(f"  Agencias nuevas creadas desde asociados_migrar.csv ({len(created)}): {created}")

    def text(row, key):
        return s(row, key) or None

    def flag(row, key):
        value = s(row, key).lower()
        return True if value == "t" else False if value == "f" else None

    records = []
    for row in rows:
        id_number = s(row, "idasociado")
        agencia = s(row, "agencia")
        if not id_number:
            note_discard("tercero_sin_idasociado", row)
            continue

        # Sin agencia o sin apellido se migran igual (V34): Access los tiene asi, no se inventa nada.
        grower_type = s(row, "tipo").upper()[:1] or "C"
        records.append(
            (
                id_number,
                s(row, "1er nombre"),
                s(row, "2o nombre") or None,
                s(row, "1er apellido") or None,
                s(row, "2o apellido") or None,
                agency_map.get(agencia),
                parse_date(row.get("fechanacimiento")),
                s(row, "direccion"),
                s(row, "telefono"),
                parse_date(row.get("fechaafiliación")),
                grower_type,
                parse_bool(row.get("habil")) and parse_bool(row.get("aceptado")),
                parse_bool(row.get("fallecido")),
                parse_bool(row.get("retirado")),
                text(row, "emp_transp"),
                text(row, "vehiculo"),
                text(row, "sexo"),
                flag(row, "asociacion"),
                flag(row, "aceptado"),
                flag(row, "habil"),
                text(row, "estadocivil"),
                text(row, "lugarnacimiento"),
                text(row, "cedulacafetera"),
                text(row, "acta"),
                text(row, "observacion"),
                text(row, "ciu"),
                text(row, "codigopostal"),
                text(row, "email"),
                parse_int(s(row, "numregistro"), None),
                flag(row, "exportado"),
                flag(row, "nuevo"),
                text(row, "pais"),
            )
        )

    psycopg2.extras.execute_values(
        cur,
        """
        INSERT INTO terceros (
            identificacion, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
            agencia_id, fecha_nacimiento, direccion, telefono, fecha_afiliacion,
            tipo, activo, fallecido, retirado,
            empresa_transporte, vehiculo, sexo, asociacion, aceptado, habil,
            estado_civil, lugar_nacimiento, cedula_cafetera, acta, observacion,
            ciudad, codigo_postal, email, numero_registro, exportado, nuevo, pais
        ) VALUES %s
        ON CONFLICT (identificacion) DO UPDATE SET
            primer_nombre = EXCLUDED.primer_nombre,
            segundo_nombre = EXCLUDED.segundo_nombre,
            primer_apellido = EXCLUDED.primer_apellido,
            segundo_apellido = EXCLUDED.segundo_apellido,
            agencia_id = EXCLUDED.agencia_id,
            fecha_nacimiento = EXCLUDED.fecha_nacimiento,
            direccion = EXCLUDED.direccion,
            telefono = EXCLUDED.telefono,
            fecha_afiliacion = EXCLUDED.fecha_afiliacion,
            tipo = EXCLUDED.tipo,
            activo = EXCLUDED.activo,
            fallecido = EXCLUDED.fallecido,
            retirado = EXCLUDED.retirado,
            empresa_transporte = EXCLUDED.empresa_transporte,
            vehiculo = EXCLUDED.vehiculo,
            sexo = EXCLUDED.sexo,
            asociacion = EXCLUDED.asociacion,
            aceptado = EXCLUDED.aceptado,
            habil = EXCLUDED.habil,
            estado_civil = EXCLUDED.estado_civil,
            lugar_nacimiento = EXCLUDED.lugar_nacimiento,
            cedula_cafetera = EXCLUDED.cedula_cafetera,
            acta = EXCLUDED.acta,
            observacion = EXCLUDED.observacion,
            ciudad = EXCLUDED.ciudad,
            codigo_postal = EXCLUDED.codigo_postal,
            email = EXCLUDED.email,
            numero_registro = EXCLUDED.numero_registro,
            exportado = EXCLUDED.exportado,
            nuevo = EXCLUDED.nuevo,
            pais = EXCLUDED.pais
        """,
        records,
    )
    print(f"  {len(records)} terceros insertados/actualizados.")


def load_announcements(cur, el_tambo_id, rp_fund_id):
    rows = read_csv("anuncios_migrar.csv")
    inserted = updated = numbered = 0
    for row in rows:
        number = s(row, "anuncio")
        special_type = s(row, "especial")
        cur.execute(
            "SELECT id FROM anuncios WHERE agencia_id = %s AND numero_anuncio = %s",
            (el_tambo_id, number),
        )
        existing = cur.fetchone()
        if existing:
            cur.execute(
                "UPDATE anuncios SET especial = %s WHERE id = %s",
                (special_type, existing[0]),
            )
            announcement_id = existing[0]
            updated += 1
        else:
            announcement_id = insert_announcement(cur, row, number, special_type, el_tambo_id, rp_fund_id)
            inserted += 1
        # La numeracion por agencia que V16 sacaba de los anuncios ya cargados: el numero de Access.
        cur.execute(
            "INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
            " VALUES (%s, %s, %s, %s) ON CONFLICT (agencia_id, numero_anuncio) DO NOTHING",
            (el_tambo_id, announcement_id, int(number), parse_date(row["fecha"])),
        )
        numbered += cur.rowcount
    print(f"  {inserted} anuncios insertados, {updated} actualizados (especial), {numbered} numeros en anuncios_agencias.")


def insert_announcement(cur, row, number, special_type, el_tambo_id, rp_fund_id):
    cur.execute(
        """
        INSERT INTO anuncios (
            numero_anuncio, fecha_anuncio, pr_base_cps,
            pr_alm_defec, pr_alm_sana, bonificacion, costos,
            agencia_id, fondo_id, especial, activo
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, true)
        RETURNING id
        """,
        (
            number,
            parse_date(row["fecha"]),
            parse_decimal(row["pr_base_cps"]),
            parse_decimal(row["pr_almdefec"]),
            parse_decimal(row["pr_almsana"]),
            parse_decimal(row["bonificacion"]),
            parse_decimal(row["costos"]),
            el_tambo_id,
            rp_fund_id,
            special_type,
        ),
    )
    return cur.fetchone()[0]


def load_dry_coffee_purchases(cur, el_tambo_id, admin_user_id):
    cur.execute("SELECT id, codigo FROM fondos")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute("SELECT id, codigo FROM productos")
    product_by_code = {code: pid for pid, code in cur.fetchall()}
    cur.execute("SELECT identificacion, primer_nombre, primer_apellido, direccion, telefono, tipo FROM terceros")
    growers = {r[0]: r[1:] for r in cur.fetchall()}

    cur.execute("SELECT numero_factura FROM compras_cafe_seco")
    existing_invoices = {r[0] for r in cur.fetchall()}

    rows = read_csv("compras_migrar.csv")
    records = []
    for row in rows:
        invoice_number = parse_int(row["factura"])
        # Anuladas (V37): se cargan tal cual estan en Access (AnularFactura ya dejo sus montos en 0).
        status = "ANULADA" if s(row, "forma_de_pago") == "ANULADA" else "VALIDA"
        if invoice_number in existing_invoices:
            continue
        cedula = s(row, "cedula")
        grower = growers.get(cedula)
        if grower is None:
            note_discard("compra_sin_caficultor", (invoice_number, cedula))
            continue
        first_name, last_name, address, phone, grower_type = grower

        fpch = parse_decimal(row.get("fpch"))
        if fpch > 0:
            payment_method = "CHEQUE"
            numcheque = s(row, "numcheque")
            check_number = numcheque if numcheque and numcheque != "0" else None
        else:
            payment_method = "EFECTIVO"
            check_number = None

        purchase_ts = parse_timestamp(row["fecha_compra"]) or datetime.now()

        records.append(
            (
                parse_date(row["fecha_compra"]),
                invoice_number,
                el_tambo_id,
                fund_by_code[s(row, "fondo")],
                s(row, "especial"),
                product_by_code[s(row, "cod_prod")],
                s(row, "anuncio"),
                parse_date(row["fecha_anuncio"]),
                parse_decimal(row["pr_base_pc"]),
                cedula,
                first_name,
                last_name,
                grower_type,
                address,
                phone,
                parse_int(row["sacos"]),
                parse_decimal(row["kilos_brutos"]),
                parse_decimal(row["destare"]),
                parse_decimal(row["kilos_netos"]),
                parse_decimal(row["w_totalm"]),
                parse_decimal(row["porcmerma"]),
                parse_decimal(row["w_almdefec"]),
                parse_decimal(row["porcalmdefec"]),
                parse_decimal(row["w_almsana"]),
                parse_decimal(row["porcalmsana"]),
                parse_decimal(row["pr_almsana"]),
                parse_decimal(row["pr_almdefec"]),
                parse_decimal(row["bonificación"]),
                parse_decimal(row["castigo"]),
                parse_decimal(row["costos"]),
                parse_decimal(row["vr_kilo"]),
                parse_decimal(row["vr_bruto"]),
                parse_decimal(row["vr_inventario"]),
                parse_decimal(row["aporte_socio"]),
                parse_decimal(row["descuento_coop"]),
                parse_decimal(row["retefuente"]) == 0,
                parse_decimal(row["retefuente"]),
                parse_decimal(row["descuento_fro"]),
                parse_decimal(row["otrosdescuentos"]),
                parse_decimal(row["neto_a_pagar"]),
                payment_method,
                check_number,
                admin_user_id,
                purchase_ts,
                status,
            )
        )

    psycopg2.extras.execute_values(
        cur,
        """
        INSERT INTO compras_cafe_seco (
            fecha_compra, numero_factura, agencia_id, fondo_id, especial,
            producto_id, numero_anuncio, fecha_anuncio, pr_base_cps,
            cedula, nombres, apellidos, tipo_vendedor, direccion, celular,
            sacos, kilos_brutos, destare, kilos_netos, peso_tot_alm,
            porc_merma, peso_tot_pasilla, porc_kg_pasilla,
            peso_alm_sana, factor, pr_alm_sana,
            pr_alm_defec, bonificacion, castigo, costos, vr_kilo, vr_bruto,
            vr_inventario, aporte_asociado, descuento_cooperativo,
            exento_retencion, retefuente, descuento_financiero, otros_descuentos,
            neto_a_pagar, forma_pago, numero_cheque, creado_por_usuario_id, creado_en, estado
        ) VALUES %s
        """,
        records,
    )
    print(f"  {len(records)} compras_cafe_seco insertadas.")


def load_check_relation_marks(cur, el_tambo_id):
    """Caja.Rel_cheques tal cual estaba en Access: solo se marca lo que ya estaba marcado."""
    cur.execute(
        "SELECT numero_factura, numero_cheque, id FROM compras_cafe_seco WHERE agencia_id = %s",
        (el_tambo_id,),
    )
    purchase_by_invoice = {inv: (check, pid) for inv, check, pid in cur.fetchall()}
    marks = []
    for row in read_csv("caja_migrar.csv"):
        check = parse_int(s(row, "cheque", "0"))
        if check <= 0 or s(row, "rel_cheques") != "t":
            continue
        invoice = parse_int(s(row, "id_transaccion"), None)
        purchase = purchase_by_invoice.get(invoice)
        if purchase is None or parse_int(purchase[0], None) != check:
            note_discard("Rel_cheques marcado sin compra que lo respalde", s(row, "id_transaccion"))
            continue
        marks.append(("DRY", purchase[1]))
    psycopg2.extras.execute_values(
        cur,
        "INSERT INTO cheques_relacionados (origen, origen_id) VALUES %s ON CONFLICT DO NOTHING",
        marks,
    )
    print(f"  {len(marks)} cheques ya relacionados en Access marcados.")


PURCHASE_TABLES = ("compras_cafe_seco", "compras_cafe_verde", "compras_pasilla")


def load_manual_cash_entries(cur, el_tambo_id):
    """
    Filas de Caja que no son pagos de compras (cruces de almacen, cheques cobrados, ajustes).
    El sistema nuevo arma al leer las copias que Access hacia entre Caja y Suministros, asi que
    cada fila se guarda una sola vez, en la tabla donde nacio:
      - Caja con Entradas y cheque, copiada a Suministros (Efectivo con cheques): movimientos_caja
        CHEQUE; el CAMBIO A EFECTIVO se aplica al leer.
      - Caja con Salidas y cheque que vino de Suministros (Cheques girados a caja): suministros.
      - El resto (incluidos cheques sin copia en Suministros): movimientos_caja con su Forma_de_Pago.
    Rel_cheques = t pasa a cheques_relacionados. Idempotente: una fila igual ya migrada no se repite.
    """
    cur.execute("SELECT id, codigo FROM fondos")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute(" UNION ALL ".join(f"SELECT numero_factura FROM {t} WHERE agencia_id = %(a)s"
                                   for t in PURCHASE_TABLES), {"a": el_tambo_id})
    invoices = {str(r[0]) for r in cur.fetchall()}

    supply_checks = {}
    for row in read_csv("suministros_migrar.csv"):
        if s(row, "forma_de_pago") == "CHEQUE":
            key = (s(row, "id_transaccion"), parse_decimal(s(row, "vr_gastos_o_comp")))
            supply_checks[key] = supply_checks.get(key, 0) + 1

    existing = {}
    for table in ("movimientos_caja", "suministros"):
        cur.execute(
            f"SELECT id, id_transaccion, fecha, entradas, salidas, numero_cheque FROM {table}"
            " WHERE agencia_id = %s ORDER BY id",
            (el_tambo_id,),
        )
        for eid, *key in cur.fetchall():
            existing.setdefault((table, *key), []).append(eid)

    counts = {}
    for row in read_csv("caja_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        if transaction_id in invoices:
            continue
        inflow = parse_decimal(s(row, "entradas"))
        outflow = parse_decimal(s(row, "salidas"))
        check = parse_int(s(row, "cheque", "0")) or None
        method = s(row, "forma_de_pago")
        table = "movimientos_caja"
        copy_key = (transaction_id, inflow if inflow > 0 else outflow)
        if check and supply_checks.get(copy_key, 0) > 0:
            supply_checks[copy_key] -= 1
            method = "CHEQUE"
            if outflow > 0:
                table = "suministros"
        entry_date = parse_date(s(row, "fecha"))
        ids = existing.get((table, transaction_id, entry_date, inflow, outflow, check))
        if ids:
            entry_id = ids.pop(0)
        else:
            cur.execute(
                f"INSERT INTO {table} (id_transaccion, agencia_id, fondo_id, fecha, cedula, detalle,"
                " entradas, salidas, forma_pago, numero_cheque, creado_en)"
                " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id",
                (transaction_id, el_tambo_id, fund_by_code[s(row, "fondo")], entry_date, s(row, "cedula"),
                 s(row, "detalle") or None, inflow, outflow, method, check, parse_timestamp(s(row, "fecha"))),
            )
            entry_id = cur.fetchone()[0]
        if s(row, "rel_cheques") == "t":
            cur.execute(
                "INSERT INTO cheques_relacionados (origen, origen_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
                ("SUPPLY" if table == "suministros" else "CASH", entry_id),
            )
        counts[(table, method)] = counts.get((table, method), 0) + 1
    for (table, method), n in sorted(counts.items()):
        print(f"  {table} {method}: {n} movimientos manuales de Caja.")


# SUPUESTO (carga de prueba, decision del usuario 2026-10-01): en Access la fila de Suministros
# "sumlf60-29-05" (29/05/2026, entrada 60.000.000) tiene Fondo = "j", que no es RP ni LF. Se toma como
# LF por el patron del propio codigo ("sumlf..."), NO por confirmacion externa. Revisar con el dato
# real de produccion. En Access esa fila no salia en los informes SuministrosRP/LF (filtran "RP"/"LF").
SUPPLY_FUND_ASSUMPTIONS = {("sumlf60-29-05", "j"): "LF"}


def load_supplies_petty_cash_and_packaging(cur, el_tambo_id):
    """
    Suministros propios, CajaMenor y Empaque de Access, tal cual. De Suministros se omiten las filas
    que el sistema nuevo ya arma al leer: pagos con cheque de compras (ActualizaSuministros) y
    cheques de Caja (Efectivo con cheques). Un Fondo que no existe en Access como RP/LF no se puede
    guardar en fondos y se reporta como descarte, sin inventar otro. Idempotente.
    """
    cur.execute("SELECT id, codigo FROM fondos")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute(" UNION ALL ".join(f"SELECT numero_factura FROM {t} WHERE agencia_id = %(a)s"
                                   for t in PURCHASE_TABLES), {"a": el_tambo_id})
    invoices = {str(r[0]) for r in cur.fetchall()}
    cash_check_inflows = {}
    for row in read_csv("caja_migrar.csv"):
        if parse_int(s(row, "cheque", "0")) > 0 and parse_decimal(s(row, "entradas")) > 0:
            key = (s(row, "id_transaccion"), parse_decimal(s(row, "entradas")))
            cash_check_inflows[key] = cash_check_inflows.get(key, 0) + 1

    def existing(table, value_cols):
        cur.execute(f"SELECT id, id_transaccion, fecha, {value_cols} FROM {table} WHERE agencia_id = %s",
                    (el_tambo_id,))
        found = {}
        for eid, *key in cur.fetchall():
            found.setdefault(tuple(key), []).append(eid)
        return found

    def insert_once(found, key, sql, values):
        if found.get(key):
            found[key].pop()
            return 0
        cur.execute(sql, values)
        return 1

    ledger_sql = ("INSERT INTO {} (id_transaccion, agencia_id, fondo_id, fecha, cedula, detalle,"
                  " entradas, salidas, forma_pago, numero_cheque, creado_en)"
                  " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)")

    found = existing("suministros", "entradas, salidas")
    inserted = 0
    for row in read_csv("suministros_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        inflow = parse_decimal(s(row, "valor_suministro"))
        outflow = parse_decimal(s(row, "vr_gastos_o_comp"))
        if transaction_id in invoices:
            continue
        copy_key = (transaction_id, outflow)
        if s(row, "forma_de_pago") == "CHEQUE" and cash_check_inflows.get(copy_key, 0) > 0:
            cash_check_inflows[copy_key] -= 1
            continue
        fund = SUPPLY_FUND_ASSUMPTIONS.get((transaction_id, s(row, "fondo")), s(row, "fondo"))
        if fund not in fund_by_code:
            note_discard("Suministros con Fondo inexistente", (transaction_id, s(row, "fondo"), str(inflow)))
            continue
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("suministros"),
            (transaction_id, el_tambo_id, fund_by_code[fund], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None,
             parse_int(s(row, "cheque", "0")) or None, parse_timestamp(s(row, "fecha"))))
    print(f"  suministros: {inserted} filas propias de Suministros.")

    found = existing("movimientos_caja_menor", "entradas, salidas")
    inserted = 0
    for row in read_csv("cajamenor_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_decimal(s(row, "entradas")), parse_decimal(s(row, "salidas"))
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("movimientos_caja_menor"),
            (transaction_id, el_tambo_id, fund_by_code[s(row, "fondo")], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None, None,
             parse_timestamp(s(row, "fecha"))))
    print(f"  movimientos_caja_menor: {inserted} filas de CajaMenor.")

    found = existing("empaques", "entradas, salidas")
    inserted = 0
    for row in read_csv("empaque_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_int(s(row, "entradas")), parse_int(s(row, "salidas"))
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow),
            "INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula,"
            " detalle, entradas, salidas, creado_en) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (transaction_id, el_tambo_id, s(row, "tipo"), entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, parse_timestamp(s(row, "fecha"))))
    print(f"  empaques: {inserted} filas de Empaque.")


def load_purchase_payment_splits(cur, el_tambo_id):
    """
    Compras de Access pagadas con mas de una forma (FPef/FPch/FPtx/FPdat > 0 en mas de una). La
    compra migrada guarda el neto bajo una sola forma; los informes de Suministros leen este desglose
    para armar Caja/Suministros como Access (una fila por forma). Si las partes no suman el neto, la
    fila se reporta y no se carga. No toca compras_cafe_seco. Idempotente (upsert por compra).
    """
    cur.execute(
        "SELECT numero_factura, id, neto_a_pagar FROM compras_cafe_seco WHERE agencia_id = %s",
        (el_tambo_id,),
    )
    purchase_by_invoice = {inv: (pid, net) for inv, pid, net in cur.fetchall()}
    splits = []
    for row in read_csv("compras_migrar.csv"):
        parts = [parse_decimal(s(row, k)) for k in ("fpef", "fpch", "fptx", "fpdat")]
        if sum(1 for p in parts if p > 0) < 2:
            continue
        invoice = parse_int(s(row, "factura"), None)
        purchase = purchase_by_invoice.get(invoice)
        if purchase is None:
            note_discard("Pago mixto sin compra migrada", invoice)
            continue
        if sum(parts) != purchase[1]:
            note_discard("Pago mixto que no suma el neto", (invoice, [str(p) for p in parts], str(purchase[1])))
            continue
        splits.append(("DRY", purchase[0], *parts, parse_int(s(row, "numcheque"), None) or None))
    psycopg2.extras.execute_values(
        cur,
        """
        INSERT INTO formas_pago_compras (origen, origen_id, efectivo, cheque,
            transferencia, datafono, numero_cheque) VALUES %s
        ON CONFLICT (origen, origen_id) DO UPDATE SET
            efectivo = EXCLUDED.efectivo, cheque = EXCLUDED.cheque,
            transferencia = EXCLUDED.transferencia, datafono = EXCLUDED.datafono,
            numero_cheque = EXCLUDED.numero_cheque
        """,
        splits,
    )
    print(f"  formas_pago_compras: {len(splits)} compras con pago mixto.")


def load_annulment_locks(cur, el_tambo_id):
    """COMPRAS.Exportado = Si en Access: compra cerrada en el sistema anterior, que no se puede anular
    (V5, anulacion_bloqueada)."""
    purchases = [
        (parse_bool(row["exportado"]), el_tambo_id, parse_int(row["factura"]))
        for row in read_csv("compras_migrar.csv")
    ]
    psycopg2.extras.execute_batch(
        cur,
        "UPDATE compras_cafe_seco SET anulacion_bloqueada = %s WHERE agencia_id = %s AND numero_factura = %s",
        purchases,
    )
    print(f"  Anulacion bloqueada: {sum(1 for p in purchases if p[0])} de {len(purchases)} compras.")


# ---------- Parte B: NESS ----------

def load_ness(cur):
    """Tabla NESS de Access tal cual (cupos_ness guarda todo como texto). Se vacia y se recarga."""
    rows = [tuple(s(r, k) or None for k in ("cedula", "nombres", "programa", "cupo"))
            for r in read_csv("ness_migrar.csv")]
    cur.execute("TRUNCATE TABLE cupos_ness")
    psycopg2.extras.execute_values(cur, "INSERT INTO cupos_ness (cedula, nombres, programa, cupo) VALUES %s", rows)
    print(f"  cupos_ness: {len(rows)} filas cargadas.")


# ---------- usuario de pruebas ----------

def ensure_admin_eltambo(cur, el_tambo_id):
    """admin_eltambo (ADMINISTRADOR, El Tambo) para los tests de integracion. Contrasena aleatoria en cada corrida:
    no queda en el codigo ni se imprime; si se necesita entrar con ese usuario, se cambia desde la app."""
    password = secrets.token_urlsafe(32).encode()
    cur.execute(
        "INSERT INTO usuarios (usuario, contrasena_hash, activo, rol_id, agencia_id)"
        " SELECT 'admin_eltambo', %s, true, id, %s FROM roles WHERE nombre = 'ADMINISTRADOR'"
        " ON CONFLICT (usuario) DO NOTHING",
        (bcrypt.hashpw(password, bcrypt.gensalt()).decode(), el_tambo_id),
    )
    print("  admin_eltambo creado." if cur.rowcount else "  admin_eltambo ya existe, se omite.")


# ---------- main ----------

def main():
    env = dotenv_values(BACKEND_DIR / ".env")
    conn = psycopg2.connect(
        host="localhost",
        port=5433,
        dbname=os.environ.get("DB_NAME", env["DB_NAME"]),  # DB_NAME del entorno manda (ensayos)
        user=env["DB_USER"],
        password=env["DB_PASSWORD"],
    )
    try:
        with conn:
            with conn.cursor() as cur:
                print("Parte A - tablas reales")
                el_tambo_id = load_agency_el_tambo(cur)
                print(f"  Agencia 'El Tambo' id={el_tambo_id}")
                load_control_record(cur, el_tambo_id)
                load_growers(cur)
                cur.execute("SELECT id FROM fondos WHERE codigo = 'RP'")
                rp_fund_id = cur.fetchone()[0]
                load_announcements(cur, el_tambo_id, rp_fund_id)
                cur.execute("SELECT id FROM usuarios WHERE usuario = 'admin'")
                admin_user_id = cur.fetchone()[0]
                load_dry_coffee_purchases(cur, el_tambo_id, admin_user_id)
                load_check_relation_marks(cur, el_tambo_id)
                load_manual_cash_entries(cur, el_tambo_id)
                load_supplies_petty_cash_and_packaging(cur, el_tambo_id)
                load_purchase_payment_splits(cur, el_tambo_id)
                load_annulment_locks(cur, el_tambo_id)
                ensure_admin_eltambo(cur, el_tambo_id)

                print("\nParte B - NESS")
                load_ness(cur)
    finally:
        conn.close()

    print("\nResumen de descartes:")
    for reason, items in DISCARDED.items():
        print(f"  {reason}: {len(items)}")
        for item in items[:5]:
            print(f"    ejemplo: {item}")


if __name__ == "__main__":
    main()
