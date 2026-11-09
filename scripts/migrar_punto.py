"""
Carga de un punto de compra de Access a Postgres, desde los CSV de exportar_access_csv.py.

Uso:
  python scripts/migrar_punto.py --base cafeoccidente_test --carpeta docs/legacy-postgres-reference/eltambo
  ... --desde 2026-01-01      fecha de corte de la historia (compras, caja, suministros, caja menor, empaques)
  ... --aplicar               sin esto es modo prueba: todo en una transaccion que se deshace al final
  ... --admin-pruebas         crea admin_eltambo (solo para la base de pruebas)

Parte A: Registro de Control, terceros, anuncios, compras (seco y pasilla), caja, suministros, caja menor,
empaques, formas de pago, compras a futuro y consecutivos. Idempotente: no duplica si se vuelve a correr.
- La agencia sale del Registro de Control del punto; si no existe en la web, se detiene.
- Registro de Control: si falta un parametro se detiene (nunca pone 0); si la agencia ya tiene uno en la web,
  no lo toca y lista las diferencias con Access.
- Terceros: inserta los nuevos y no sobrescribe los que ya estan; las diferencias van a diferencias_terceros.csv.
- Con --desde se cargan igual las compras sin remesar, todos los anuncios, las compras a futuro vigentes y el
  Registro de Control, para que los saldos cuadren.
- Compras a futuro: solo las de saldo > 0 y entrega no vencida; las vencidas se listan. Cupos de anuncio: no se
  cargan (P8), se listan.
- Consecutivos de factura y remision: el siguiente de la web es el ultimo de Access + 1 (nunca se bajan).

Parte B (solo con --ness): cupos_ness con la tabla NESS de Access. Se vacia y se recarga, y borra la NESS que se
sube con Actualiza Cupos NESS (HU-54): por eso no corre por defecto.

Dependencias: scripts/requirements.txt.
"""

import argparse
import csv
import os
import re
import secrets
import subprocess
from datetime import date, datetime, timedelta, timezone
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


# Access guarda la hora local de Colombia (sin horario de verano). Sin zona, PostgreSQL la leia como UTC
# y creado_en quedaba 5 h antes (16/01 19:00 en vez de 17/01 00:00).
ACCESS_ZONE = timezone(timedelta(hours=-5), "America/Bogota")


def parse_timestamp(value):
    """Fecha y hora de Access, en hora de Colombia; None si viene vacia o no se entiende."""
    value = (value or "").strip()
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(value[:19], fmt).replace(tzinfo=ACCESS_ZONE)
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


def parse_text(value):
    return (value or "").strip()


# ---------- reglas (sin base; las prueba scripts/test_migrar_punto.py) ----------

# RegControl de Access -> registros_control. Los verdes (peso_*_cv, pr_*_cv) no existen en Access: van en 0 (Q23).
CONTROL_FIELDS = [
    ("numregistro", "numero_registro_control", parse_int),
    ("kgexcelso", "kg_excelso", parse_int),
    ("baseretefte", "base_retencion", parse_decimal),
    ("basecarga", "base_carga", parse_int),
    ("porcretefte", "porcentaje_retencion", parse_decimal),
    ("basepasilla", "base_pasilla", parse_decimal),
    ("porckgpasprom", "porc_kg_pasilla_promedio", parse_decimal),
    ("prefijo", "prefijo", parse_text),
    ("costos", "costos", parse_decimal),
    ("muestra", "tamano_muestra", parse_decimal),
    ("umbralespeciales", "umbral_cafes_especiales", parse_decimal),
    ("porcverde", "porcentaje_cafe_verde", parse_decimal),
    ("factorbase", "factor_base", parse_decimal),
    # Validado contra compras_migrar.csv: el descuento coop observado (0,8 %) es porcdesccoop.
    ("porcasociados", "porc_aportes_asociados", parse_decimal),
    ("porcdesccoop", "porc_desc_coop_no_asociados", parse_decimal),
    ("fiel", "cedula_fiel", parse_text),
    ("resdian", "resolucion_dian", parse_text),
    ("fecharesdian", "fecha_resolucion", parse_date),
    ("desderes", "desde_resolucion", parse_int),
    ("hastares", "hasta_resolucion", parse_int),
    ("vigenciares", "vigencia", parse_int),
]


def missing_control_params(reg):
    """Parametros del RegControl vacios en Access (decision del usuario: la carga se detiene, nunca pone 0)."""
    return sorted(key for key, _, _ in CONTROL_FIELDS if not (reg.get(key) or "").strip())


def control_record_differences(web, reg):
    """[(columna, valor en la web, valor en Access)] de las columnas de `web` que no coinciden."""
    return [(column, str(web[column]), str(parse(reg.get(key))))
            for key, column, parse in CONTROL_FIELDS
            if column in web and parse(reg.get(key)) != web[column]]


def keeps_purchase(row, desde):
    """Con fecha de corte entran las compras desde esa fecha y, de antes, las validas sin remesar (Plano = f)."""
    bought = parse_date(row.get("fecha_compra"))
    if desde is None or bought is None or bought >= desde:
        return True
    return s(row, "plano") != "t" and s(row, "forma_de_pago") != "ANULADA"


def husk_purchase(row):
    """Access guarda la pasilla en COMPRAS con Especial = PASILLA; en la web va a compras_pasilla."""
    return s(row, "especial").upper() == "PASILLA"


# Porcentajes de COMPRAS que van en columnas numeric(6,2): hasta 9.999,99.
PERCENT_LIMIT = Decimal("9999.995")


def out_of_range(row):
    """Porcentajes de la compra que no caben en la web (un peso mal digitado en Access). No se corrigen: la compra
    se lista y no se carga."""
    columns = ("porcalmsana",) if husk_purchase(row) else ("porcmerma", "porcalmdefec", "porcalmsana")
    return [c for c in columns if abs(parse_decimal(row.get(c))) >= PERCENT_LIMIT]


def split_future_purchases(rows, today):
    """(se cargan, vencidas) de las compras a futuro con saldo: decision del usuario, solo las no vencidas."""
    load, expired = [], []
    for row in rows:
        if parse_decimal(row.get("saldo_kilos")) <= 0:
            continue
        delivery = parse_date(row.get("fecha_entrega"))
        (load if delivery is not None and delivery >= today else expired).append(row)
    return load, expired


def quota_announcements(rows):
    """Anuncios de Access con cupo: no se cargan (P8), se listan para que el Administrador decida."""
    return [row for row in rows if parse_decimal(row.get("cupo")) > 0]


def max_invoice(rows):
    """Mayor factura de COMPRAS de Access, tambien las anuladas (el consecutivo no se reutiliza)."""
    return max((parse_int(row.get("factura")) for row in rows), default=0)


def max_remission(rows):
    """Mayor numero de remision de Access (TA0968 -> 968); 0 si no hay."""
    numbers = [int(m.group(1)) for row in rows if (m := re.search(r"(\d+)$", s(row, "remision")))]
    return max(numbers, default=0)


def transaction_collisions(ids, prefix):
    """Id de Access con el formato de la web (PREFIJO + digito + codigo + 3 cifras): chocarian con un consecutivo."""
    pattern = re.compile(rf"^{re.escape(prefix)}\d[A-Z]{{0,3}}\d{{3}}$", re.IGNORECASE)
    return [i for i in ids if pattern.match(i)]


# ---------- Parte A ----------

def load_agency(cur):
    """La agencia del punto, por el nombre que trae su RegControl. Nunca se crea: las 22 ya existen (Q25)."""
    reg = read_csv("regcontrol_migrar.csv")[0]
    cur.execute("SELECT nombre_access, id FROM agencias WHERE nombre_access IS NOT NULL")
    by_access = dict(cur.fetchall())
    target = canonical_access_value(s(reg, "agencia"), by_access)
    if target not in by_access:
        raise SystemExit(f"La agencia '{s(reg, 'agencia')}' del RegControl no existe en la web: no se carga nada.")
    cur.execute("SELECT nombre FROM agencias WHERE id = %s", (by_access[target],))
    return by_access[target], cur.fetchone()[0]


def load_control_record(cur, agency_id):
    reg = read_csv("regcontrol_migrar.csv")[0]
    columns = [column for _, column, _ in CONTROL_FIELDS]
    cur.execute(f"SELECT {', '.join(columns)} FROM registros_control WHERE agencia_id = %s AND activo",
                (agency_id,))
    existing = cur.fetchone()
    if existing:
        differences = control_record_differences(dict(zip(columns, existing)), reg)
        print(f"  Registro de Control: ya existe en la web, no se toca. Diferencias con Access: {len(differences)}")
        for column, web, access in differences:
            print(f"    {column}: web {web} / Access {access}")
        return
    missing = missing_control_params(reg)
    if missing:
        raise SystemExit(f"El RegControl de Access no tiene: {', '.join(missing)}. Se completa en Access o en la"
                         " web antes de migrar; no se carga nada.")
    cur.execute(
        f"INSERT INTO registros_control (agencia_id, activo, punto_compra, {', '.join(columns)},"
        " peso_tot_alm_cv, peso_tot_pasilla_cv, peso_alm_sana_cv, pr_alm_sana_cv, pr_alm_defec_cv)"
        f" VALUES (%s, true, %s, {', '.join(['%s'] * len(columns))}, 0, 0, 0, 0, 0)",
        (agency_id, s(reg, "agencia"), *[parse(reg.get(key)) for key, _, parse in CONTROL_FIELDS]),
    )
    print("  Registro de Control insertado.")


# Valor de la columna Agencia de asociados_migrar.csv -> nombre_access de la agencia real. Los valores
# que ya son un nombre_access no van aqui; uno que no este en ningun lado aborta la carga.
AGENCY_ALIASES = {
    # Mayusculas o tildes distintas a la semilla
    "Ancuya": "ANCUYA",
    "Buesaco": "BUESACO1 OCCIDENTE",
    "Chachagui": "CHACHAGUI",
    "Consaca": "CONSACA",
    "El Tambo": "EL TAMBO",
    "La Florida": "LA FLORIDA",
    "Linares": "LINARES",
    "Samaniego": "SAMANIEGO",
    # Errores de escritura
    "BUESACO2 OCCIDEN TE": "BUESACO2",
    "BUESACO2 OCCIDENTE": "BUESACO2",
    "BUESACO2 OCCIDENTEÇ": "BUESACO2",
    "EL INGNENIO": "EL INGENIO",
    "INGENIO": "EL INGENIO",
    "EL TAMB0": "EL TAMBO",
    "EL TAMO": "EL TAMBO",
    "EL TAMBO (N)": "EL TAMBO",
    "LA FLORIDA (N)": "LA FLORIDA",
    "SAMANEIGO": "SAMANIEGO",
    # Otro nombre del mismo punto
    "PASTO SEDE": "PASTO",
    "SOTOMAYOR": "LOS ANDES",
    "Sotomayor": "LOS ANDES",
    "LOS ANDES SOTOMAYOR": "LOS ANDES",
    # No son puntos de compra: sus asociados se facturan como Pasto (area de compras, 2026-10-26)
    "GUAITARILLA": "PASTO",
    "Guaitarilla": "PASTO",
    "Santacruz": "PASTO",
    "POLICARPA": "PASTO",
    "TANGUA": "PASTO",
}

# Agencias de Access que no estan entre los 20 de la semilla (V2):
# nombre_access -> (nombre, activa, prefijo de factura sugerido para su Registro de Control).
# Buesaco 2 es punto de compra; BUESACO (sin numero) no lo es: queda inactiva con sus asociados como en
# Access (area de compras, 2026-10-26).
EXTRA_AGENCIES = {
    "BUESACO2": ("Buesaco 2", True, "SDB2"),
    "BUESACO": ("BUESACO", False, None),
}


def canonical_access_value(value, known_access_values):
    """nombre_access de la agencia real de un valor crudo de Access; None si no tiene equivalencia."""
    target = AGENCY_ALIASES.get(value, value)
    return target if target in known_access_values or target in EXTRA_AGENCIES else None


def resolve_agencies(cur, raw_values):
    """Agencia real de cada valor de 'agencia' en asociados_migrar.csv. Solo crea las de EXTRA_AGENCIES."""
    cur.execute("SELECT nombre_access, id FROM agencias WHERE nombre_access IS NOT NULL")
    by_access = dict(cur.fetchall())
    unknown = sorted(v for v in raw_values if canonical_access_value(v, by_access) is None)
    if unknown:
        raise SystemExit(f"Valores de Agencia sin equivalencia (agregarlos a AGENCY_ALIASES): {unknown}")

    value_to_id = {}
    created = []
    for value in sorted(raw_values):
        target = canonical_access_value(value, by_access)
        if target not in by_access:
            name, active, _ = EXTRA_AGENCIES[target]
            cur.execute(
                "INSERT INTO agencias (nombre, nombre_access, activa) VALUES (%s, %s, %s) RETURNING id",
                (name, target, active),
            )
            by_access[target] = cur.fetchone()[0]
            created.append(name)
        value_to_id[value] = by_access[target]
    insert_suggested_prefixes(cur, by_access)
    return value_to_id, created


def insert_suggested_prefixes(cur, by_access):
    """El prefijo sugerido de las agencias extra que son punto de compra (lo prellena su Registro de Control)."""
    for access_value, (_, _, prefix) in EXTRA_AGENCIES.items():
        if prefix and access_value in by_access:
            cur.execute(
                "INSERT INTO prefijos_sugeridos (agencia_id, prefijo) VALUES (%s, %s) ON CONFLICT (agencia_id) DO NOTHING",
                (by_access[access_value], prefix),
            )


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

    cur.execute("SELECT identificacion, primer_nombre, primer_apellido, tipo, direccion, telefono, agencia_id"
                " FROM terceros WHERE identificacion = ANY(%s)", ([r[0] for r in records],))
    existing = {row[0]: row[1:] for row in cur.fetchall()}
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
        ON CONFLICT (identificacion) DO NOTHING
        """,
        records,
    )
    report_grower_differences(existing, records)
    print(f"  terceros: {len(records) - len(existing)} nuevos; {len(existing)} ya estaban y no se tocan.")


GROWER_COMPARED = ("primer_nombre", "primer_apellido", "tipo", "direccion", "telefono", "agencia_id")


def report_grower_differences(existing, records):
    """Terceros que ya estaban en la web y Access trae distinto: se listan en diferencias_terceros.csv, no se tocan."""
    rows = []
    for record in records:
        web = existing.get(record[0])
        if web is None:
            continue
        access = (record[1], record[3], record[10], record[7], record[8], record[5])
        rows += [(record[0], column, w, a) for column, w, a in zip(GROWER_COMPARED, web, access)
                 if (w or None) != (a or None)]
    path = DATA_DIR / "diferencias_terceros.csv"
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(("identificacion", "columna", "web", "access"))
        writer.writerows(rows)
    print(f"  terceros con diferencias: {len({r[0] for r in rows})} ({len(rows)} campos) -> {path}")


def load_announcements(cur, agency_id, rp_fund_id):
    rows = read_csv("anuncios_migrar.csv")
    inserted = updated = numbered = 0
    for row in rows:
        number = s(row, "anuncio")
        special_type = s(row, "especial")
        # El numero de Access vive en anuncios_agencias (las columnas viejas de anuncios se borraron en V11).
        cur.execute(
            "SELECT anuncio_id FROM anuncios_agencias WHERE agencia_id = %s AND numero_anuncio = %s",
            (agency_id, int(number)),
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
            announcement_id = insert_announcement(cur, row, special_type, rp_fund_id)
            inserted += 1
        # La numeracion por agencia que V16 sacaba de los anuncios ya cargados: el numero de Access.
        cur.execute(
            "INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
            " VALUES (%s, %s, %s, %s) ON CONFLICT (agencia_id, numero_anuncio) DO NOTHING",
            (agency_id, announcement_id, int(number), parse_date(row["fecha"])),
        )
        numbered += cur.rowcount
    print(f"  {inserted} anuncios insertados, {updated} actualizados (especial), {numbered} numeros en anuncios_agencias.")


def insert_announcement(cur, row, special_type, rp_fund_id):
    # Pr Sustentacion, Bonificacion por kilo y Costos no se guardan: las compras los calculan con el
    # Registro de Control vivo (AnnouncementServiceImpl.toResponse). SobrePr_CPS (bonificacion por carga)
    # y VrIncCalidad si son del anuncio.
    cur.execute(
        """
        INSERT INTO anuncios (
            fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp, pr_inc_calidad,
            fondo_id, especial, activo
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, true)
        RETURNING id
        """,
        (
            parse_date(row["fecha"]),
            parse_decimal(row["pr_base_cps"]),
            parse_decimal(row["pr_almdefec"]),
            parse_decimal(row["sobrepr_cps"]),
            parse_decimal(row["vrinccalidad"]),
            rp_fund_id,
            special_type,
        ),
    )
    return cur.fetchone()[0]


PURCHASE_TABLES = ("compras_cafe_seco", "compras_cafe_verde", "compras_pasilla")


def purchase_rows(desde):
    """Filas de COMPRAS que entran con la fecha de corte."""
    return [row for row in read_csv("compras_migrar.csv") if keeps_purchase(row, desde)]


def payment_of(row):
    """Forma de pago y cheque de la compra (el desglose de pagos mixtos va en formas_pago_compras)."""
    if parse_decimal(row.get("fpch")) > 0:
        numcheque = s(row, "numcheque")
        return "CHEQUE", numcheque if numcheque and numcheque != "0" else None
    return "EFECTIVO", None


# Columnas comunes de compras_cafe_seco y compras_pasilla, en el orden de common_values().
COMMON_COLUMNS = (
    "fecha_compra, numero_factura, agencia_id, fondo_id, especial, producto_id, numero_anuncio, fecha_anuncio,"
    " pr_base_cps, cedula, nombres, apellidos, tipo_vendedor, direccion, celular, sacos, kilos_brutos, destare,"
    " kilos_netos, costos, vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo,"
    " exento_retencion, retefuente, descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago,"
    " numero_cheque, creado_por_usuario_id, creado_en, estado"
)


def load_purchases(cur, agency_id, admin_user_id, desde):
    """COMPRAS de Access: cafe seco a compras_cafe_seco y PASILLA a compras_pasilla (decision del usuario)."""
    cur.execute("SELECT id, codigo FROM fondos")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute("SELECT id, codigo FROM productos")
    product_by_code = {code: pid for pid, code in cur.fetchall()}
    cur.execute("SELECT identificacion, primer_nombre, primer_apellido, direccion, telefono, tipo FROM terceros")
    growers = {r[0]: r[1:] for r in cur.fetchall()}
    # La factura es unica por agencia y compartida por seco, verde y pasilla (Q7).
    cur.execute(" UNION ALL ".join(f"SELECT numero_factura FROM {t} WHERE agencia_id = %(a)s"
                                   for t in PURCHASE_TABLES), {"a": agency_id})
    existing_invoices = {r[0] for r in cur.fetchall()}

    def common_values(row, invoice_number, grower):
        first_name, last_name, address, phone, grower_type = grower
        payment_method, check_number = payment_of(row)
        return (
            parse_date(row["fecha_compra"]), invoice_number, agency_id, fund_by_code[s(row, "fondo")],
            s(row, "especial"), product_by_code[s(row, "cod_prod")], s(row, "anuncio"),
            parse_date(row["fecha_anuncio"]), parse_decimal(row["pr_base_pc"]),
            s(row, "cedula"), first_name, last_name, grower_type, address, phone, parse_int(row["sacos"]),
            parse_decimal(row["kilos_brutos"]), parse_decimal(row["destare"]), parse_decimal(row["kilos_netos"]),
            parse_decimal(row["costos"]), parse_decimal(row["vr_kilo"]), parse_decimal(row["vr_bruto"]),
            parse_decimal(row["vr_inventario"]), parse_decimal(row["aporte_socio"]),
            parse_decimal(row["descuento_coop"]), parse_decimal(row["retefuente"]) == 0,
            parse_decimal(row["retefuente"]), parse_decimal(row["descuento_fro"]),
            parse_decimal(row["otrosdescuentos"]), parse_decimal(row["neto_a_pagar"]), payment_method,
            check_number, admin_user_id, parse_timestamp(row["fecha_compra"]) or datetime.now(ACCESS_ZONE),
            # Anuladas (V37): se cargan tal cual estan en Access (AnularFactura ya dejo sus montos en 0).
            "ANULADA" if s(row, "forma_de_pago") == "ANULADA" else "VALIDA",
        )

    dry, husk = [], []
    for row in purchase_rows(desde):
        invoice_number = parse_int(row["factura"])
        if invoice_number in existing_invoices:
            continue
        grower = growers.get(s(row, "cedula"))
        if grower is None:
            note_discard("compra_sin_caficultor", (invoice_number, s(row, "cedula")))
            continue
        bad = out_of_range(row)
        if bad:
            note_discard("compra con un porcentaje que no cabe (no se carga)",
                         (invoice_number, {c: s(row, c) for c in bad}))
            continue
        values = common_values(row, invoice_number, grower)
        if husk_purchase(row):
            # Pasilla de Access: Pr_AlmSana = Precio Por Punto, W_AlmSana = peso de almendra, PorcAlmSana = %.
            # Pr Base es el Pr_Base_CPS del anuncio (la pasilla no descuenta costos de carga).
            husk.append(values[:8] + (parse_decimal(row["pr_base_cps"]),) + values[9:] + (
                parse_decimal(row["pr_almsana"]), parse_decimal(row["w_almsana"]), parse_decimal(row["porcalmsana"])))
        else:
            dry.append(values + (
                parse_decimal(row["w_totalm"]), parse_decimal(row["porcmerma"]), parse_decimal(row["w_almdefec"]),
                parse_decimal(row["porcalmdefec"]), parse_decimal(row["w_almsana"]), parse_decimal(row["porcalmsana"]),
                parse_decimal(row["pr_almsana"]), parse_decimal(row["pr_almdefec"]), parse_decimal(row["bonificación"]),
                parse_decimal(row["castigo"])))

    psycopg2.extras.execute_values(
        cur,
        f"INSERT INTO compras_cafe_seco ({COMMON_COLUMNS}, peso_tot_alm, porc_merma, peso_tot_pasilla,"
        " porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion, castigo) VALUES %s",
        dry,
    )
    psycopg2.extras.execute_values(
        cur,
        f"INSERT INTO compras_pasilla ({COMMON_COLUMNS}, pr_punto, peso_almendra, porc_almendra) VALUES %s",
        husk,
    )
    print(f"  compras: {len(dry)} de cafe seco y {len(husk)} de pasilla insertadas.")


def purchases_by_invoice(cur, agency_id, column):
    """{factura: (columna, id, origen)} de las compras de seco y pasilla de la agencia."""
    cur.execute(f"SELECT numero_factura, {column}, id, 'DRY' FROM compras_cafe_seco WHERE agencia_id = %(a)s"
                f" UNION ALL SELECT numero_factura, {column}, id, 'HUSK' FROM compras_pasilla WHERE agencia_id = %(a)s",
                {"a": agency_id})
    return {inv: (value, pid, origin) for inv, value, pid, origin in cur.fetchall()}


def before_cut(row, desde):
    """Movimiento de Caja, Suministros, Caja Menor o Empaque anterior a la fecha de corte."""
    moved = parse_date(row.get("fecha"))
    return desde is not None and moved is not None and moved < desde


def load_check_relation_marks(cur, agency_id):
    """Caja.Rel_cheques tal cual estaba en Access: solo se marca lo que ya estaba marcado."""
    purchase_by_invoice = purchases_by_invoice(cur, agency_id, "numero_cheque")
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
        marks.append((purchase[2], purchase[1]))
    psycopg2.extras.execute_values(
        cur,
        "INSERT INTO cheques_relacionados (origen, origen_id) VALUES %s ON CONFLICT DO NOTHING",
        marks,
    )
    print(f"  {len(marks)} cheques ya relacionados en Access marcados.")


def load_manual_cash_entries(cur, agency_id, desde):
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
                                   for t in PURCHASE_TABLES), {"a": agency_id})
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
            (agency_id,),
        )
        for eid, *key in cur.fetchall():
            existing.setdefault((table, *key), []).append(eid)

    counts = {}
    for row in read_csv("caja_migrar.csv"):
        transaction_id = s(row, "id_transaccion")
        if transaction_id in invoices or before_cut(row, desde):
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
                (transaction_id, agency_id, fund_by_code[s(row, "fondo")], entry_date, s(row, "cedula"),
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


def load_supplies_petty_cash_and_packaging(cur, agency_id, desde):
    """
    Suministros propios, CajaMenor y Empaque de Access, tal cual. De Suministros se omiten las filas
    que el sistema nuevo ya arma al leer: pagos con cheque de compras (ActualizaSuministros) y
    cheques de Caja (Efectivo con cheques). Un Fondo que no existe en Access como RP/LF no se puede
    guardar en fondos y se reporta como descarte, sin inventar otro. Idempotente.
    """
    cur.execute("SELECT id, codigo FROM fondos")
    fund_by_code = {code: fid for fid, code in cur.fetchall()}
    cur.execute(" UNION ALL ".join(f"SELECT numero_factura FROM {t} WHERE agencia_id = %(a)s"
                                   for t in PURCHASE_TABLES), {"a": agency_id})
    invoices = {str(r[0]) for r in cur.fetchall()}
    cash_check_inflows = {}
    for row in read_csv("caja_migrar.csv"):
        if parse_int(s(row, "cheque", "0")) > 0 and parse_decimal(s(row, "entradas")) > 0:
            key = (s(row, "id_transaccion"), parse_decimal(s(row, "entradas")))
            cash_check_inflows[key] = cash_check_inflows.get(key, 0) + 1

    def existing(table, value_cols):
        cur.execute(f"SELECT id, id_transaccion, fecha, {value_cols} FROM {table} WHERE agencia_id = %s",
                    (agency_id,))
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
        if transaction_id in invoices or before_cut(row, desde):
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
            (transaction_id, agency_id, fund_by_code[fund], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None,
             parse_int(s(row, "cheque", "0")) or None, parse_timestamp(s(row, "fecha"))))
    print(f"  suministros: {inserted} filas propias de Suministros.")

    found = existing("movimientos_caja_menor", "entradas, salidas")
    inserted = 0
    for row in read_csv("cajamenor_migrar.csv"):
        if before_cut(row, desde):
            continue
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_decimal(s(row, "entradas")), parse_decimal(s(row, "salidas"))
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("movimientos_caja_menor"),
            (transaction_id, agency_id, fund_by_code[s(row, "fondo")], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None, None,
             parse_timestamp(s(row, "fecha"))))
    print(f"  movimientos_caja_menor: {inserted} filas de CajaMenor.")

    found = existing("empaques", "entradas, salidas")
    inserted = 0
    for row in read_csv("empaque_migrar.csv"):
        if before_cut(row, desde):
            continue
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_int(s(row, "entradas")), parse_int(s(row, "salidas"))
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow),
            "INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula,"
            " detalle, entradas, salidas, creado_en) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (transaction_id, agency_id, s(row, "tipo"), entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, parse_timestamp(s(row, "fecha"))))
    print(f"  empaques: {inserted} filas de Empaque.")


def load_purchase_payment_splits(cur, agency_id, desde):
    """
    Compras de Access pagadas con mas de una forma (FPef/FPch/FPtx/FPdat > 0 en mas de una). La
    compra migrada guarda el neto bajo una sola forma; los informes de Suministros leen este desglose
    para armar Caja/Suministros como Access (una fila por forma). Si las partes no suman el neto, la
    fila se reporta y no se carga. No toca compras_cafe_seco. Idempotente (upsert por compra).
    """
    purchase_by_invoice = purchases_by_invoice(cur, agency_id, "neto_a_pagar")
    splits = []
    for row in purchase_rows(desde):
        parts = [parse_decimal(s(row, k)) for k in ("fpef", "fpch", "fptx", "fpdat")]
        if sum(1 for p in parts if p > 0) < 2:
            continue
        invoice = parse_int(s(row, "factura"), None)
        purchase = purchase_by_invoice.get(invoice)
        if purchase is None:
            note_discard("Pago mixto sin compra migrada", invoice)
            continue
        if sum(parts) != purchase[0]:
            note_discard("Pago mixto que no suma el neto", (invoice, [str(p) for p in parts], str(purchase[0])))
            continue
        splits.append((purchase[2], purchase[1], *parts, parse_int(s(row, "numcheque"), None) or None))
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


def load_annulment_locks(cur, agency_id, desde):
    """COMPRAS.Exportado = Si en Access: compra cerrada en el sistema anterior, que no se puede anular
    (V5, anulacion_bloqueada). Vale para seco y pasilla."""
    purchases = [(parse_bool(row["exportado"]), agency_id, parse_int(row["factura"])) for row in purchase_rows(desde)]
    for table in ("compras_cafe_seco", "compras_pasilla"):
        psycopg2.extras.execute_batch(
            cur,
            f"UPDATE {table} SET anulacion_bloqueada = %s WHERE agencia_id = %s AND numero_factura = %s",
            purchases,
        )
    print(f"  Anulacion bloqueada: {sum(1 for p in purchases if p[0])} de {len(purchases)} compras.")


def load_future_purchases(cur, agency_id, admin_user_id, today):
    """COMPRAS A FUTURO con saldo y entrega no vencida (decision del usuario); las vencidas solo se listan."""
    load, expired = split_future_purchases(read_csv("compras_a_futuro_migrar.csv"), today)
    cur.execute("SELECT identificacion, primer_nombre, primer_apellido, tipo, direccion FROM terceros"
                " WHERE identificacion = ANY(%s)", ([s(r, "cedula") for r in load],))
    growers = {r[0]: r[1:] for r in cur.fetchall()}
    inserted = 0
    for row in load:
        grower = growers.get(s(row, "cedula"))
        if grower is None:
            note_discard("compra_a_futuro_sin_tercero", (s(row, "anuncio"), s(row, "cedula")))
            continue
        cur.execute(
            "INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio, cedula, nombres,"
            " apellidos, tipo, direccion, especial, kilos, saldo, fecha_entrega, finca, municipio, vereda,"
            " pr_alm_sana, pr_alm_defec, bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id)"
            " SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s"
            " WHERE NOT EXISTS (SELECT 1 FROM compras_anunciadas WHERE agencia_id = %s AND numero_anuncio = %s"
            " AND cedula = %s)",
            (parse_timestamp(row["fecha_anuncio"]), agency_id, s(row, "anuncio"), parse_date(row["fecha_anuncio"]),
             s(row, "cedula"), grower[0], grower[1], grower[2] or "C", grower[3] or "", s(row, "especial"),
             parse_decimal(row["kilos_anunciados"]), parse_decimal(row["saldo_kilos"]),
             parse_date(row["fecha_entrega"]), s(row, "finca") or None, s(row, "mpio") or None,
             s(row, "vereda") or None, parse_decimal(row["pr_almsana"]), parse_decimal(row["pr_almdefec"]),
             parse_decimal(row["bonificación"]), parse_decimal(row["costos"]), parse_decimal(row["vrinccalidad"]),
             parse_decimal(row["prbcps"]), admin_user_id, agency_id, s(row, "anuncio"), s(row, "cedula")),
        )
        inserted += cur.rowcount
    print(f"  compras a futuro: {inserted} cargadas (saldo y entrega vigente); {len(expired)} vencidas no se cargan.")
    for row in expired:
        print(f"    vencida: anuncio {s(row, 'anuncio')}, cedula {s(row, 'cedula')}, saldo {s(row, 'saldo_kilos')} kg,"
              f" entrega {s(row, 'fecha_entrega')[:10]}")


def list_quota_announcements():
    """P8: los cupos de anuncio de Access no se cargan; el Administrador los asigna con Asignar Cupo."""
    rows = quota_announcements(read_csv("anuncios_migrar.csv"))
    print(f"  cupos de anuncio en Access (no se cargan, P8): {len(rows)}")
    for row in rows:
        print(f"    anuncio {s(row, 'anuncio')} ({s(row, 'especial')}): cupo {s(row, 'cupo')},"
              f" entregados {s(row, 'entregados')}, saldo {s(row, 'saldo')}")


def set_consecutives(cur, agency_id):
    """Factura y remision siguen a Access (ultimo + 1, nunca se bajan). Anuncios: la web numera con el mayor
    numero_anuncio de la agencia. Id de transaccion: Access los digitaba libres; se revisa que ninguno choque."""
    compras = read_csv("compras_migrar.csv")
    prefix = s(read_csv("regcontrol_migrar.csv")[0], "prefijo")
    ids = [s(r, "id_transaccion") for name in ("caja", "suministros", "cajamenor", "empaque")
           for r in read_csv(f"{name}_migrar.csv")]
    collisions = transaction_collisions(ids, prefix)
    if collisions:
        raise SystemExit(f"Id de transaccion de Access con el formato de la web (chocarian): {collisions[:10]}")

    rows = []
    access_invoice = max_invoice(compras)
    cur.execute("SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = %s", (agency_id,))
    web = (cur.fetchone() or [None])[0]
    cur.execute("INSERT INTO consecutivos_factura (agencia_id, ultimo_numero) VALUES (%(a)s, %(n)s)"
                " ON CONFLICT (agencia_id) DO UPDATE SET ultimo_numero ="
                " GREATEST(consecutivos_factura.ultimo_numero, EXCLUDED.ultimo_numero) RETURNING ultimo_numero",
                {"a": agency_id, "n": access_invoice})
    rows.append(("factura", access_invoice, web, cur.fetchone()[0] + 1))

    access_remission = max_remission(compras)
    for code in ("RP", "LF"):
        cur.execute("SELECT r.ultimo_consecutivo FROM consecutivos_remision r JOIN fondos f ON f.id = r.fondo_id"
                    " WHERE r.agencia_id = %s AND f.codigo = %s", (agency_id, code))
        web = (cur.fetchone() or [None])[0]
        if access_remission == 0:
            rows.append((f"remision {code}", 0, web, (web or 0) + 1))
            continue
        # Access lleva una sola serie de remisiones por punto; la web una por punto y fondo (RN-11.5).
        cur.execute("INSERT INTO consecutivos_remision (agencia_id, fondo_id, ultimo_consecutivo)"
                    " SELECT %(a)s, id, %(n)s FROM fondos WHERE codigo = %(c)s"
                    " ON CONFLICT (agencia_id, fondo_id) DO UPDATE SET ultimo_consecutivo ="
                    " GREATEST(consecutivos_remision.ultimo_consecutivo, EXCLUDED.ultimo_consecutivo)"
                    " RETURNING ultimo_consecutivo", {"a": agency_id, "n": access_remission, "c": code})
        rows.append((f"remision {code}", access_remission, web, cur.fetchone()[0] + 1))

    access_announcement = max((parse_int(r.get("anuncio")) for r in read_csv("anuncios_migrar.csv")), default=0)
    cur.execute("SELECT max(numero_anuncio) FROM anuncios_agencias WHERE agencia_id = %s", (agency_id,))
    web = cur.fetchone()[0]
    rows.append(("anuncio", access_announcement, web, (web or 0) + 1))

    print("  Consecutivos (ultimo en Access / ultimo en la web / siguiente en la web):")
    for name, access, before, following in rows:
        shown = "-" if before is None else before
        print(f"    {name:<12} {access:>8} / {shown:>8} / {following}")
    print(f"    Id de transaccion: {len(ids)} de Access, ninguno con el formato {prefix}<codigo><3 cifras>.")


# ---------- Parte B: NESS ----------

def load_ness(cur):
    """Tabla NESS de Access tal cual (cupos_ness guarda todo como texto). Se vacia y se recarga."""
    rows = [tuple(s(r, k) or None for k in ("cedula", "nombres", "programa", "cupo"))
            for r in read_csv("ness_migrar.csv")]
    cur.execute("TRUNCATE TABLE cupos_ness")
    psycopg2.extras.execute_values(cur, "INSERT INTO cupos_ness (cedula, nombres, programa, cupo) VALUES %s", rows)
    print(f"  cupos_ness: {len(rows)} filas cargadas.")


# ---------- usuario de pruebas ----------

def ensure_admin_eltambo(cur, agency_id):
    """admin_eltambo (ADMINISTRADOR, El Tambo) para los tests de integracion. Contrasena aleatoria en cada corrida:
    no queda en el codigo ni se imprime; si se necesita entrar con ese usuario, se cambia desde la app."""
    password = secrets.token_urlsafe(32).encode()
    cur.execute(
        "INSERT INTO usuarios (usuario, contrasena_hash, activo, rol_id, agencia_id)"
        " SELECT 'admin_eltambo', %s, true, id, %s FROM roles WHERE nombre = 'ADMINISTRADOR'"
        " ON CONFLICT (usuario) DO NOTHING",
        (bcrypt.hashpw(password, bcrypt.gensalt()).decode(), agency_id),
    )
    print("  admin_eltambo creado." if cur.rowcount else "  admin_eltambo ya existe, se omite.")


# ---------- main ----------

def backup(base):
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    target = ROOT / "docs" / "respaldos-bd" / f"{base}_antes_migrar_punto_{stamp}.dump"
    with target.open("wb") as out:
        subprocess.run(["docker", "exec", "cafeoccidente-postgres", "pg_dump", "-U", "postgres", "-Fc", base],
                       check=True, stdout=out)
    return target


def main():
    global DATA_DIR
    parser = argparse.ArgumentParser(description="Carga un punto de compra de Access (CSV) en la web.")
    parser.add_argument("--base", required=True, help="cafeoccidente_test, cafeoccidente u otra base local")
    parser.add_argument("--carpeta", default=str(DATA_DIR), help="carpeta con los *_migrar.csv del punto")
    parser.add_argument("--desde", type=date.fromisoformat, help="fecha de corte AAAA-MM-DD (opcional)")
    parser.add_argument("--aplicar", action="store_true", help="guarda los cambios (sin esto, modo prueba)")
    parser.add_argument("--admin-pruebas", action="store_true", help="crea admin_eltambo (base de pruebas)")
    parser.add_argument("--ness", action="store_true",
                        help="recarga cupos_ness con la NESS de esta base (borra la que se subio con HU-54)")
    args = parser.parse_args()
    DATA_DIR = Path(args.carpeta)

    env = dotenv_values(BACKEND_DIR / ".env")
    if args.aplicar:
        print(f"Respaldo: {backup(args.base)}")
    conn = psycopg2.connect(host="localhost", port=5433, dbname=args.base, user=env["DB_USER"],
                            password=env["DB_PASSWORD"])
    try:
        with conn.cursor() as cur:
            print(f"Parte A - {DATA_DIR}" + (f" (desde {args.desde})" if args.desde else ""))
            agency_id, agency_name = load_agency(cur)
            print(f"  Agencia '{agency_name}' id={agency_id}")
            load_control_record(cur, agency_id)
            load_growers(cur)
            cur.execute("SELECT id FROM fondos WHERE codigo = 'RP'")
            rp_fund_id = cur.fetchone()[0]
            load_announcements(cur, agency_id, rp_fund_id)
            list_quota_announcements()
            cur.execute("SELECT id FROM usuarios WHERE usuario = 'admin'")
            admin_user_id = cur.fetchone()[0]
            load_purchases(cur, agency_id, admin_user_id, args.desde)
            load_check_relation_marks(cur, agency_id)
            load_manual_cash_entries(cur, agency_id, args.desde)
            load_supplies_petty_cash_and_packaging(cur, agency_id, args.desde)
            load_purchase_payment_splits(cur, agency_id, args.desde)
            load_annulment_locks(cur, agency_id, args.desde)
            load_future_purchases(cur, agency_id, admin_user_id, datetime.now(ACCESS_ZONE).date())
            set_consecutives(cur, agency_id)
            if args.admin_pruebas:
                ensure_admin_eltambo(cur, agency_id)

            if args.ness:
                print("\nParte B - NESS")
                load_ness(cur)
            else:
                print("\nParte B - NESS: no se toca (la vigente es la que se sube en Actualiza Cupos NESS).")
        if args.aplicar:
            conn.commit()
            print("\nCambios guardados.")
        else:
            conn.rollback()
            print("\nModo prueba (sin --aplicar): no se guardo nada.")
    finally:
        conn.close()

    print("\nResumen de descartes:")
    for reason, items in DISCARDED.items():
        print(f"  {reason}: {len(items)}")
        for item in items[:5]:
            print(f"    ejemplo: {item}")


if __name__ == "__main__":
    main()
