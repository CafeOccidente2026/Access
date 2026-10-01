"""
Carga de datos reales de El Tambo (Access -> Postgres).

Fuente de verdad: docs/legacy-postgres-reference/eltambo/*.csv +
docs/legacy-postgres-reference/PosgreSQL/*.sql (estructura original).

Parte A: agency, control_record, grower, announcement, dry_coffee_purchase
(tablas reales ya modeladas por JPA/Flyway). Idempotente: upsert por
llave natural, no duplica si se vuelve a correr.

Parte B: staging_legacy_* (caja, cajamenor, suministros, empaque,
inventario, cupos, ness, compras_a_futuro) - solo para tener los datos
disponibles antes de que existan esos modulos reales. Se truncan y
recargan en cada corrida.
"""

import csv
import re
import unicodedata
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

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


def ascii_column(name):
    name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode("ascii")
    name = re.sub(r"[^a-zA-Z0-9]+", "_", name).strip("_").lower()
    return name or "col"


# ---------- Parte A ----------

def load_agency_el_tambo(cur):
    reg = read_csv("regcontrol_migrar.csv")[0]
    access_value = s(reg, "agencia")
    prefix = s(reg, "prefijo")
    cur.execute("SELECT id, invoice_prefix FROM agency WHERE access_agency_value = %s", (access_value,))
    row = cur.fetchone()
    if row:
        agency_id, current_prefix = row
        if current_prefix != prefix:
            cur.execute("UPDATE agency SET invoice_prefix = %s WHERE id = %s", (prefix, agency_id))
        return agency_id
    cur.execute(
        "INSERT INTO agency (name, access_agency_value, invoice_prefix, active) "
        "VALUES (%s, %s, %s, true) RETURNING id",
        ("El Tambo", access_value, prefix),
    )
    return cur.fetchone()[0]


def load_control_record(cur, el_tambo_id):
    reg = read_csv("regcontrol_migrar.csv")[0]
    purchase_point = s(reg, "agencia")
    prefix = s(reg, "prefijo")
    cur.execute(
        "SELECT id FROM control_record WHERE purchase_point = %s AND prefix = %s",
        (purchase_point, prefix),
    )
    if cur.fetchone():
        print("  control_record de El Tambo ya existe, se omite.")
        return

    cur.execute(
        """
        INSERT INTO control_record (
            agency_id, active, control_number, base_factor, base_withholding, base_load,
            withholding_percentage, base_husk, avg_husk_percentage, purchase_point,
            prefix, costs, sample_size, excelso_kg, green_coffee_percentage,
            specialty_threshold, associate_percentage, non_associate_discount,
            trusted_id, dian_resolution, resolution_date, resolution_from,
            resolution_to, validity
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
            # (base_factor observado = 70 = kgexcelso, no factorbase;
            # specialty_threshold observado = 94 = factorbase, no umbralespeciales).
            parse_int(reg["kgexcelso"]),  # -> base_factor
            parse_decimal(reg["baseretefte"]),
            parse_int(reg["basecarga"]),
            parse_decimal(reg["porcretefte"]),
            parse_decimal(reg["basepasilla"]),
            parse_decimal(reg["porckgpasprom"]),
            purchase_point,
            prefix,
            parse_decimal(reg["costos"]),
            parse_decimal(reg["muestra"]),
            # excelso_kg no se usa en ningun calculo (ver DryCoffeePurchaseCalculator);
            # se deja igual a kgexcelso ya que no hay evidencia de su fuente real correcta.
            parse_decimal(reg["kgexcelso"]),
            parse_decimal(reg["porcverde"]),
            parse_decimal(reg["factorbase"]),  # -> specialty_threshold
            # NOTE: nombres de columnas del CSV estan cruzados respecto al orden de
            # los campos en ControlRecord - validado contra compras_migrar.csv real
            # (descuento_coop observado = 0.8% = porcdesccoop, no porcasociados).
            parse_decimal(reg["porcasociados"]),  # -> associate_percentage
            parse_decimal(reg["porcdesccoop"]),  # -> non_associate_discount
            s(reg, "fiel"),
            s(reg, "resdian"),
            parse_date(reg["fecharesdian"]),
            parse_int(reg["desderes"]),
            parse_int(reg["hastares"]),
            parse_int(reg["vigenciares"]),
        ),
    )
    print("  control_record de El Tambo insertado.")


def resolve_agencies(cur, raw_values):
    """Resuelve/crea una Agency por cada valor crudo distinto de 'agencia' en asociados_migrar.csv."""
    cur.execute("SELECT id, name, access_agency_value FROM agency")
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
                "INSERT INTO agency (name, access_agency_value, invoice_prefix, active) "
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
        print(f"  Agencies nuevas creadas desde asociados_migrar.csv ({len(created)}): {created}")

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
            note_discard("grower_sin_idasociado", row)
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
        INSERT INTO grower (
            id_number, first_name, second_name, last_name, second_last_name,
            agency_id, birth_date, address, phone, affiliation_date,
            grower_type, active, deceased, withdrawn,
            transport_company, vehicle_plate, sex, is_association, accepted, eligible,
            marital_status, birth_place, coffee_id_card, act_number, observation,
            city_code, postal_code, email, registry_number, exported, is_new, country
        ) VALUES %s
        ON CONFLICT (id_number) DO UPDATE SET
            first_name = EXCLUDED.first_name,
            second_name = EXCLUDED.second_name,
            last_name = EXCLUDED.last_name,
            second_last_name = EXCLUDED.second_last_name,
            agency_id = EXCLUDED.agency_id,
            birth_date = EXCLUDED.birth_date,
            address = EXCLUDED.address,
            phone = EXCLUDED.phone,
            affiliation_date = EXCLUDED.affiliation_date,
            grower_type = EXCLUDED.grower_type,
            active = EXCLUDED.active,
            deceased = EXCLUDED.deceased,
            withdrawn = EXCLUDED.withdrawn,
            transport_company = EXCLUDED.transport_company,
            vehicle_plate = EXCLUDED.vehicle_plate,
            sex = EXCLUDED.sex,
            is_association = EXCLUDED.is_association,
            accepted = EXCLUDED.accepted,
            eligible = EXCLUDED.eligible,
            marital_status = EXCLUDED.marital_status,
            birth_place = EXCLUDED.birth_place,
            coffee_id_card = EXCLUDED.coffee_id_card,
            act_number = EXCLUDED.act_number,
            observation = EXCLUDED.observation,
            city_code = EXCLUDED.city_code,
            postal_code = EXCLUDED.postal_code,
            email = EXCLUDED.email,
            registry_number = EXCLUDED.registry_number,
            exported = EXCLUDED.exported,
            is_new = EXCLUDED.is_new,
            country = EXCLUDED.country
        """,
        records,
    )
    print(f"  {len(records)} growers insertados/actualizados.")


def load_announcements(cur, el_tambo_id, rp_fund_id):
    rows = read_csv("anuncios_migrar.csv")
    inserted = 0
    updated = 0
    for row in rows:
        number = s(row, "anuncio")
        special_type = s(row, "especial")
        cur.execute(
            "SELECT id FROM announcement WHERE agency_id = %s AND announcement_number = %s",
            (el_tambo_id, number),
        )
        existing = cur.fetchone()
        if existing:
            cur.execute(
                "UPDATE announcement SET special_type = %s WHERE id = %s",
                (special_type, existing[0]),
            )
            updated += 1
            continue
        cur.execute(
            """
            INSERT INTO announcement (
                announcement_number, announcement_date, base_price_load,
                defective_unit_price, healthy_unit_price, bonus, costs,
                agency_id, fund_id, special_type, active
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, true)
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
        inserted += 1
    print(f"  {inserted} announcements insertados, {updated} actualizados (special_type).")


def load_dry_coffee_purchases(cur, el_tambo_id, admin_user_id):
    cur.execute("SELECT id, code FROM fund")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute("SELECT id, code FROM product_code")
    product_by_code = {code: pid for pid, code in cur.fetchall()}
    cur.execute("SELECT id_number, first_name, last_name, address, phone, grower_type FROM grower")
    growers = {r[0]: r[1:] for r in cur.fetchall()}

    cur.execute("SELECT invoice_number FROM dry_coffee_purchase")
    existing_invoices = {r[0] for r in cur.fetchall()}

    rows = read_csv("compras_migrar.csv")
    records = []
    for row in rows:
        invoice_number = parse_int(row["factura"])
        if s(row, "forma_de_pago") == "ANULADA":
            note_discard("compra_anulada", invoice_number)
            continue
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
            )
        )

    psycopg2.extras.execute_values(
        cur,
        """
        INSERT INTO dry_coffee_purchase (
            purchase_date, invoice_number, agency_id, fund_id, special_type,
            product_code_id, announcement_number, announcement_date, base_price_load,
            id_number, first_name, last_name, grower_type, address, cellphone,
            bags_count, gross_kg, tare_kg, net_kg, total_stored_weight,
            waste_percentage, defective_stored_weight, defective_percentage,
            healthy_stored_weight, healthy_percentage, healthy_unit_price,
            defective_unit_price, bonus, penalty, costs, unit_price, gross_value,
            inventory_value, associate_contribution, cooperative_discount,
            withholding_exempt, withholding, freight_discount, other_discounts,
            net_to_pay, payment_method, check_number, created_by_user_id, created_at
        ) VALUES %s
        """,
        records,
    )
    print(f"  {len(records)} dry_coffee_purchase insertadas.")


def load_check_relation_marks(cur, el_tambo_id):
    """Caja.Rel_cheques tal cual estaba en Access: solo se marca lo que ya estaba marcado."""
    cur.execute(
        "SELECT invoice_number, check_number, id FROM dry_coffee_purchase WHERE agency_id = %s",
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
        "INSERT INTO check_relation_mark (source, source_id) VALUES %s ON CONFLICT DO NOTHING",
        marks,
    )
    print(f"  {len(marks)} cheques ya relacionados en Access marcados.")


PURCHASE_TABLES = ("dry_coffee_purchase", "green_coffee_purchase", "husk_purchase",
                   "other_coffee_purchase", "ferti_futuro_purchase")


def load_manual_cash_entries(cur, el_tambo_id):
    """
    Filas de Caja que no son pagos de compras (cruces de almacen, cheques cobrados, ajustes).
    El sistema nuevo arma al leer las copias que Access hacia entre Caja y Suministros, asi que
    cada fila se guarda una sola vez, en la tabla donde nacio:
      - Caja con Entradas y cheque, copiada a Suministros (Efectivo con cheques): cash_entry
        CHEQUE; el CAMBIO A EFECTIVO se aplica al leer.
      - Caja con Salidas y cheque que vino de Suministros (Cheques girados a caja): supply_entry.
      - El resto (incluidos cheques sin copia en Suministros): cash_entry con su Forma_de_Pago.
    Rel_cheques = t pasa a check_relation_mark. Idempotente: una fila igual ya migrada no se repite.
    """
    cur.execute("SELECT id, code FROM fund")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute(" UNION ALL ".join(f"SELECT invoice_number FROM {t} WHERE agency_id = %(a)s"
                                   for t in PURCHASE_TABLES), {"a": el_tambo_id})
    invoices = {str(r[0]) for r in cur.fetchall()}

    supply_checks = {}
    for row in read_csv("suministros_migrar.csv"):
        if s(row, "forma_de_pago") == "CHEQUE":
            key = (s(row, "id_transaccion"), parse_decimal(s(row, "vr_gastos_o_comp")))
            supply_checks[key] = supply_checks.get(key, 0) + 1

    existing = {}
    for table in ("cash_entry", "supply_entry"):
        cur.execute(
            f"SELECT id, transaction_id, entry_date, inflow, outflow, check_number FROM {table}"
            " WHERE agency_id = %s ORDER BY id",
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
        table = "cash_entry"
        copy_key = (transaction_id, inflow if inflow > 0 else outflow)
        if check and supply_checks.get(copy_key, 0) > 0:
            supply_checks[copy_key] -= 1
            method = "CHEQUE"
            if outflow > 0:
                table = "supply_entry"
        entry_date = parse_date(s(row, "fecha"))
        ids = existing.get((table, transaction_id, entry_date, inflow, outflow, check))
        if ids:
            entry_id = ids.pop(0)
        else:
            cur.execute(
                f"INSERT INTO {table} (transaction_id, agency_id, fund_id, entry_date, id_number, detail,"
                " inflow, outflow, payment_method, check_number, created_at)"
                " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id",
                (transaction_id, el_tambo_id, fund_by_code[s(row, "fondo")], entry_date, s(row, "cedula"),
                 s(row, "detalle") or None, inflow, outflow, method, check, parse_timestamp(s(row, "fecha"))),
            )
            entry_id = cur.fetchone()[0]
        if s(row, "rel_cheques") == "t":
            cur.execute(
                "INSERT INTO check_relation_mark (source, source_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
                ("SUPPLY" if table == "supply_entry" else "CASH", entry_id),
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
    guardar en fund y se reporta como descarte, sin inventar otro. Idempotente.
    """
    cur.execute("SELECT id, code FROM fund")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute(" UNION ALL ".join(f"SELECT invoice_number FROM {t} WHERE agency_id = %(a)s"
                                   for t in PURCHASE_TABLES), {"a": el_tambo_id})
    invoices = {str(r[0]) for r in cur.fetchall()}
    cash_check_inflows = {}
    for row in read_csv("caja_migrar.csv"):
        if parse_int(s(row, "cheque", "0")) > 0 and parse_decimal(s(row, "entradas")) > 0:
            key = (s(row, "id_transaccion"), parse_decimal(s(row, "entradas")))
            cash_check_inflows[key] = cash_check_inflows.get(key, 0) + 1

    def existing(table, value_cols):
        cur.execute(f"SELECT id, transaction_id, entry_date, {value_cols} FROM {table} WHERE agency_id = %s",
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

    ledger_sql = ("INSERT INTO {} (transaction_id, agency_id, fund_id, entry_date, id_number, detail,"
                  " inflow, outflow, payment_method, check_number, created_at)"
                  " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)")

    found = existing("supply_entry", "inflow, outflow")
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
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("supply_entry"),
            (transaction_id, el_tambo_id, fund_by_code[fund], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None,
             parse_int(s(row, "cheque", "0")) or None, parse_timestamp(s(row, "fecha"))))
    print(f"  supply_entry: {inserted} filas propias de Suministros.")

    found = existing("petty_cash_entry", "inflow, outflow")
    inserted = 0
    for row in read_csv("cajamenor_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_decimal(s(row, "entradas")), parse_decimal(s(row, "salidas"))
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("petty_cash_entry"),
            (transaction_id, el_tambo_id, fund_by_code[s(row, "fondo")], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None, None,
             parse_timestamp(s(row, "fecha"))))
    print(f"  petty_cash_entry: {inserted} filas de CajaMenor.")

    found = existing("packaging_entry", "inflow, outflow")
    inserted = 0
    for row in read_csv("empaque_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_int(s(row, "entradas")), parse_int(s(row, "salidas"))
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow),
            "INSERT INTO packaging_entry (transaction_id, agency_id, packaging_type, entry_date, id_number,"
            " detail, inflow, outflow, created_at) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (transaction_id, el_tambo_id, s(row, "tipo"), entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, parse_timestamp(s(row, "fecha"))))
    print(f"  packaging_entry: {inserted} filas de Empaque.")


def load_purchase_payment_splits(cur, el_tambo_id):
    """
    Compras de Access pagadas con mas de una forma (FPef/FPch/FPtx/FPdat > 0 en mas de una). La
    compra migrada guarda el neto bajo una sola forma; los informes de Suministros leen este desglose
    para armar Caja/Suministros como Access (una fila por forma). Si las partes no suman el neto, la
    fila se reporta y no se carga. No toca dry_coffee_purchase. Idempotente (upsert por compra).
    """
    cur.execute(
        "SELECT invoice_number, id, net_to_pay FROM dry_coffee_purchase WHERE agency_id = %s",
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
        INSERT INTO purchase_payment_split (source, source_id, cash_amount, check_amount,
            transfer_amount, card_amount, check_number) VALUES %s
        ON CONFLICT (source, source_id) DO UPDATE SET
            cash_amount = EXCLUDED.cash_amount, check_amount = EXCLUDED.check_amount,
            transfer_amount = EXCLUDED.transfer_amount, card_amount = EXCLUDED.card_amount,
            check_number = EXCLUDED.check_number
        """,
        splits,
    )
    print(f"  purchase_payment_split: {len(splits)} compras con pago mixto.")


def load_export_flags(cur, el_tambo_id):
    """COMPRAS.Exportado y ANUNCIOS.Exportado tal cual en Access (V36). Compras a Futuro e INVENTARIO
    de El Tambo no se migraron a tablas reales, asi que no hay filas que marcar."""
    purchases = [
        (parse_bool(row["exportado"]), el_tambo_id, parse_int(row["factura"]))
        for row in read_csv("compras_migrar.csv")
        if s(row, "forma_de_pago") != "ANULADA"
    ]
    psycopg2.extras.execute_batch(
        cur,
        "UPDATE dry_coffee_purchase SET exported = %s WHERE agency_id = %s AND invoice_number = %s",
        purchases,
    )
    announcements = [
        (parse_bool(row["exportado"]), el_tambo_id, parse_int(row["anuncio"]))
        for row in read_csv("anuncios_migrar.csv")
    ]
    psycopg2.extras.execute_batch(
        cur,
        "UPDATE agency_announcement_number SET exported = %s WHERE agency_id = %s AND announcement_number = %s",
        announcements,
    )
    print(f"  Exportado: {len(purchases)} compras y {len(announcements)} anuncios.")


# ---------- Parte B: staging ----------

STAGING_FILES = {
    "caja_migrar.csv": ("staging_legacy_caja", {"fecha"}, {"rel_cheques", "en_sum"}),
    "cajamenor_migrar.csv": ("staging_legacy_cajamenor", {"fecha"}, set()),
    "cupos_migrar.csv": ("staging_legacy_cupos", {"fecha_compra"}, set()),
    "empaque_migrar.csv": ("staging_legacy_empaque", {"fecha"}, set()),
    "inventario_migrar.csv": ("staging_legacy_inventario", {"fecha"}, {"exportado"}),
    "ness_migrar.csv": ("staging_legacy_ness", set(), set()),
    "suministros_migrar.csv": ("staging_legacy_suministros", {"fecha"}, {"en_caja"}),
    "compras_a_futuro_migrar.csv": (
        "staging_legacy_compras_a_futuro",
        {"fecha_anuncio", "fecha_entrega"},
        {"exportado"},
    ),
}


def load_staging_table(cur, csv_name, table_name, date_cols, bool_cols):
    with open(DATA_DIR / csv_name, encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader)
        rows = list(reader)

    columns = [ascii_column(h) for h in header]
    col_defs = []
    for col in columns:
        if col in date_cols:
            col_defs.append(f'"{col}" TIMESTAMP')
        elif col in bool_cols:
            col_defs.append(f'"{col}" BOOLEAN')
        else:
            col_defs.append(f'"{col}" TEXT')

    cur.execute(f'CREATE TABLE IF NOT EXISTS {table_name} (id SERIAL PRIMARY KEY, {", ".join(col_defs)})')
    cur.execute(f"TRUNCATE TABLE {table_name}")

    if not rows:
        print(f"  {table_name}: 0 filas (CSV sin datos), tabla creada/vaciada.")
        return

    values = []
    for raw_row in rows:
        record = []
        for col, raw_value in zip(columns, raw_row):
            if col in date_cols:
                record.append(parse_timestamp(raw_value))
            elif col in bool_cols:
                v = (raw_value or "").strip().lower()
                record.append(True if v == "t" else False if v == "f" else None)
            else:
                record.append(raw_value.strip() if raw_value and raw_value.strip() else None)
        values.append(tuple(record))

    col_list = ", ".join(f'"{c}"' for c in columns)
    psycopg2.extras.execute_values(
        cur, f"INSERT INTO {table_name} ({col_list}) VALUES %s", values
    )
    print(f"  {table_name}: {len(values)} filas cargadas.")


def load_all_staging(cur):
    for csv_name, (table_name, date_cols, bool_cols) in STAGING_FILES.items():
        load_staging_table(cur, csv_name, table_name, date_cols, bool_cols)


# ---------- main ----------

def main():
    env = dotenv_values(BACKEND_DIR / ".env")
    conn = psycopg2.connect(
        host="localhost",
        port=5433,
        dbname=env["DB_NAME"],
        user=env["DB_USER"],
        password=env["DB_PASSWORD"],
    )
    try:
        with conn:
            with conn.cursor() as cur:
                print("Parte A - tablas reales")
                el_tambo_id = load_agency_el_tambo(cur)
                print(f"  Agency 'El Tambo' id={el_tambo_id}")
                load_control_record(cur, el_tambo_id)
                load_growers(cur)
                cur.execute("SELECT id FROM fund WHERE code = 'RP'")
                rp_fund_id = cur.fetchone()[0]
                load_announcements(cur, el_tambo_id, rp_fund_id)
                cur.execute("SELECT id FROM users WHERE username = 'admin'")
                admin_user_id = cur.fetchone()[0]
                load_dry_coffee_purchases(cur, el_tambo_id, admin_user_id)
                load_check_relation_marks(cur, el_tambo_id)
                load_manual_cash_entries(cur, el_tambo_id)
                load_supplies_petty_cash_and_packaging(cur, el_tambo_id)
                load_purchase_payment_splits(cur, el_tambo_id)
                load_export_flags(cur, el_tambo_id)

                print("\nParte B - staging")
                load_all_staging(cur)
    finally:
        conn.close()

    print("\nResumen de descartes:")
    for reason, items in DISCARDED.items():
        print(f"  {reason}: {len(items)}")
        for item in items[:5]:
            print(f"    ejemplo: {item}")


if __name__ == "__main__":
    main()
