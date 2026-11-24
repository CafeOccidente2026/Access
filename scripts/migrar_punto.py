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
- Registro de Control: si falta un parametro se detiene (nunca pone 0); si la agencia ya tiene uno en la web, lo
  actualiza con los campos que trae Access, muestra cada diferencia y la deja en el Historial (24-nov).
- Terceros: inserta los nuevos y no sobrescribe los que ya estan; las diferencias van a diferencias_terceros.csv.
- Con --desde se cargan igual las compras sin remesar, todos los anuncios, las compras a futuro vigentes y el
  Registro de Control, para que los saldos cuadren.
- Compras a futuro: solo las de saldo > 0 y entrega no vencida; las vencidas se listan.
- Anuncio de compras y compras a futuro con el prefijo del punto ("SDTA-3490"), como la web (decision del 18-nov).
- Cupos de anuncio (se revierte P8, 18-nov): solo los que tienen saldo al corte (cupo - kilos de las compras); sus
  compras entran aunque sean de antes de --desde. Los que no tienen saldo se listan.
- Consecutivos de factura y remision: el siguiente de la web es el ultimo de Access + 1 (nunca se bajan). La
  remision/remesa lleva un solo contador por agencia (V31): con TA0968 en Access la siguiente es SDTA-969.

Parte B (solo con --ness): cupos_ness con la tabla NESS de Access. Se vacia y se recarga, y borra la NESS que se
sube con Actualiza Cupos NESS (HU-54): por eso no corre por defecto.

Dependencias: scripts/requirements.txt.
"""

import argparse
import csv
import os
import re
import unicodedata
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
    """Aviso: el registro SI se cargo tal cual, pero conviene revisarlo (p. ej. caficultor sin apellido)."""
    DISCARDED.setdefault(reason, []).append(detail)


# Regla fija de la migracion (25-nov): son datos reales de produccion; no se inventa, supone ni descarta nada.
# Lo que no se puede cargar tal cual se junta aqui y la carga se detiene al final (no se guarda nada).
STOPS = {}
# correcciones.csv (fuera del repo, lo llena el usuario con lo que confirma el area): unica forma de excluir un
# registro de Access. {(tabla, id_access): fila}. EXCLUDED: los registros que se excluyeron en esta carga.
CORRECTIONS = {}
EXCLUDED = {}
CORRECTED = {}
REMISSION_COUNTER_KEY = ("consecutivo_remision", "")
CORRECTION_TABLES = ("anuncios", "asociados", "caja", "cajamenor", "compras_a_futuro", "compras", "cupos",
                     "empaque", "inventario", "suministros")


def note_stop(reason, detail):
    STOPS.setdefault(reason, []).append(detail)


def stop_if_problems():
    """Detiene la carga (sin guardar) si hay registros de Access que no se pudieron cargar tal cual."""
    if not STOPS:
        return
    print("\nLA CARGA SE DETIENE: registros de Access que no se pueden cargar tal cual (no se guardo nada).")
    for reason, items in STOPS.items():
        print(f"  {reason}: {len(items)}")
        for item in items:
            print(f"    {item}")
    print("  Solo se pueden excluir con correcciones.csv (punto, tabla, id_access, accion, motivo, confirmado_por).")
    raise SystemExit(1)


def simple_name(text):
    """Nombre comparable: sin tildes, sin espacios ni signos, en minusculas ("El Tambo" = "ElTambo")."""
    plain = unicodedata.normalize("NFKD", text or "").encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", plain.lower())


def corrections_for(rows, point):
    """
    Correcciones del punto: {(tabla, id_access): fila}. Acciones (25-nov):
      - excluir: el registro no se carga.
      - corregir: el registro se carga con `campo` = `valor` (lo demas tal cual).
      - fijar_remision: el contador de remision del punto queda en `valor` (la siguiente es valor + 1).
    Una fila que no se entiende detiene la carga.
    """
    tables = {simple_name(t): t for t in CORRECTION_TABLES}
    found = {}
    for row in rows:
        if simple_name(row.get("punto")) != simple_name(point):
            continue
        action = s(row, "accion").lower()
        if action == "fijar_remision":
            if not s(row, "valor").isdigit() or not s(row, "motivo"):
                raise SystemExit(f"correcciones.csv: fijar_remision necesita un valor numerico y motivo: {row}")
            found[REMISSION_COUNTER_KEY] = row
            continue
        table = tables.get(simple_name(row.get("tabla")))
        ident = s(row, "id_access")
        valid = table is not None and ident.isdigit() and s(row, "motivo") and (
            action == "excluir" or (action == "corregir" and s(row, "campo") and s(row, "valor")))
        if not valid:
            raise SystemExit("correcciones.csv: fila no valida (tabla de la carga, Id de Access numerico, motivo y"
                             f" accion 'excluir', 'corregir' con campo y valor, o 'fijar_remision'): {row}")
        found[(table, ident)] = row
    return found


def fixed_remission(found):
    """Ultimo numero de remision fijado por correcciones.csv para el punto; None si no hay."""
    row = found.get(REMISSION_COUNTER_KEY)
    return int(s(row, "valor")) if row else None


def fixed_by_agency(rows, agencies):
    """{agencia_id: numero} de las filas fijar_remision de correcciones.csv. agencies: [(id, nombre, nombre_access)].
    El punto se reconoce por el nombre de la agencia o por su nombre en Access ("Sotomayor" = SOTOMAYOR)."""
    fixed = {}
    for row in rows:
        if s(row, "accion").lower() != "fijar_remision":
            continue
        point = simple_name(row.get("punto"))
        match = [aid for aid, name, access in agencies if point in (simple_name(name), simple_name(access))]
        if len(match) != 1:
            raise SystemExit(f"correcciones.csv: no se reconoce la agencia del punto '{s(row, 'punto')}'")
        fixed[match[0]] = int(s(row, "valor"))
    return fixed


def correct_rows(name, rows, found):
    """Aplica las correcciones 'corregir' a las filas del CSV; devuelve [(clave, columna, antes, despues)].
    Un campo que no existe en el CSV detiene la carga."""
    table = name.removesuffix("_migrar.csv")
    changes = []
    for row in rows:
        fix = found.get((table, row_key(table, row)))
        if not fix or s(fix, "accion").lower() != "corregir":
            continue
        column = next((c for c in row if simple_name(c) == simple_name(fix.get("campo"))), None)
        if column is None:
            raise SystemExit(f"correcciones.csv: {table} no tiene el campo '{s(fix, 'campo')}'")
        changes.append(((table, row_key(table, row)), column, row[column], s(fix, "valor")))
        row[column] = s(fix, "valor")
    return changes


def pending_corrections(found):
    """Correcciones que el area todavia no confirmo (confirmado_por vacio o PENDIENTE)."""
    return [key for key, row in found.items() if s(row, "confirmado_por").upper() in ("", "PENDIENTE")]


def row_key(table, row):
    """Id de Access del registro; en Asociados (no tiene Id en Access) la cedula, IdAsociado."""
    return s(row, "id_access") or (s(row, "idasociado") if table == "asociados" else "")


def apply_corrections(name, rows, found):
    """(filas que se cargan, filas excluidas por correcciones.csv) de un CSV del punto."""
    table = name.removesuffix("_migrar.csv")

    def excluded(row):
        fix = found.get((table, row_key(table, row)))
        return fix is not None and s(fix, "accion").lower() == "excluir"

    return [row for row in rows if not excluded(row)], [row for row in rows if excluded(row)]


def load_corrections(path, point, definitive):
    """Lee correcciones.csv, las muestra y, en la carga definitiva, se detiene si alguna sigue PENDIENTE."""
    CORRECTIONS.clear()
    if not path:
        print("  correcciones.csv: no se uso (ningun registro se excluye).")
        return
    with open(path, encoding="utf-8-sig") as f:
        CORRECTIONS.update(corrections_for(list(csv.DictReader(f)), point))
    print(f"  correcciones.csv: {len(CORRECTIONS)} para este punto.")
    pending = pending_corrections(CORRECTIONS)
    for table, ident in pending:
        fix = CORRECTIONS[(table, ident)]
        what = f"{table} Id {ident}" if ident else table
        print(f"  AVISO: correccion PENDIENTE de confirmar por el area: {s(fix, 'accion')} {what}"
              f" ({s(fix, 'motivo')})")
    if pending and definitive:
        raise SystemExit("Carga definitiva: hay correcciones PENDIENTES en correcciones.csv; no se carga nada.")


def check_corrections_used():
    """Una correccion cuyo Id no esta en el CSV del punto es un error de digitacion: detiene la carga."""
    for table, ident in CORRECTIONS:
        if (table, ident) not in EXCLUDED and (table, ident) not in CORRECTED and (table, ident) != REMISSION_COUNTER_KEY:
            note_stop("correcciones.csv con un Id que no esta en Access", f"{table} Id {ident}")


def agency_prefix(cur, agency_id):
    """Prefijo del Registro de Control activo de la agencia (SDTA), el mismo con que la web arma el anuncio."""
    cur.execute("SELECT prefijo FROM registros_control WHERE agencia_id = %s AND activo", (agency_id,))
    return cur.fetchone()[0]


# ---------- helpers ----------

def read_csv(name):
    """Filas del CSV del punto, sin las que correcciones.csv excluye (quedan en EXCLUDED para la salida)."""
    with open(DATA_DIR / name, encoding="utf-8") as f:
        rows, excluded = apply_corrections(name, list(csv.DictReader(f)), CORRECTIONS)
    for row in excluded:
        table = name.removesuffix("_migrar.csv")
        EXCLUDED[(table, row_key(table, row))] = row
    for key, column, before, after in correct_rows(name, rows, CORRECTIONS):
        CORRECTED[key] = (column, before, after)
    return rows


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


# Columna -> nombre del campo en el Historial (los de ControlRecordResponse, como los guarda la pantalla).
CONTROL_HISTORY_KEYS = {
    "numero_registro_control": "controlNumber", "kg_excelso": "excelsoKg", "base_retencion": "baseWithholding",
    "base_carga": "baseLoad", "porcentaje_retencion": "withholdingPercentage", "base_pasilla": "baseHusk",
    "porc_kg_pasilla_promedio": "avgHuskPercentage", "prefijo": "prefix", "costos": "costs",
    "tamano_muestra": "sampleSize", "umbral_cafes_especiales": "specialtyThreshold",
    "porcentaje_cafe_verde": "greenCoffeePercentage", "factor_base": "baseFactor",
    "porc_aportes_asociados": "associatePercentage", "porc_desc_coop_no_asociados": "nonAssociateDiscount",
    "cedula_fiel": "trustedId", "resolucion_dian": "dianResolution", "fecha_resolucion": "resolutionDate",
    "desde_resolucion": "resolutionFrom", "hasta_resolucion": "resolutionTo", "vigencia": "validity",
}


def control_record_updates(web, reg):
    """[(columna, valor en la base, valor de Access)] a actualizar (24-nov): los campos que Access trae y no coinciden.
    Un campo vacio en Access no borra el de la base; los que solo tiene la web (cafe verde, alerta) no estan aqui."""
    updates = []
    for key, column, parse in CONTROL_FIELDS:
        if column in web and (reg.get(key) or "").strip():
            access = parse(reg.get(key))
            if access != web[column]:
                updates.append((column, web[column], access))
    return updates


def control_record_history(updates):
    """(antes, despues) para historial_cambios, solo con los campos que cambian."""
    return ({CONTROL_HISTORY_KEYS[column]: str(web) for column, web, _ in updates},
            {CONTROL_HISTORY_KEYS[column]: str(access) for column, _, access in updates})


def grower_type(row):
    """Tipo del asociado de Access (C, F o S); None si esta vacio: no se inventa (25-nov), detiene la carga."""
    return s(row, "tipo").upper()[:1] or None


def seller_last_name(value):
    """Apellido del caficultor para la compra: vacio si Access no lo tiene (nombre completo en el primer nombre)."""
    return (value or "").strip()


def control_record_differences(web, reg):
    """[(columna, valor en la web, valor en Access)] de las columnas de `web` que no coinciden."""
    return [(column, str(web[column]), str(parse(reg.get(key))))
            for key, column, parse in CONTROL_FIELDS
            if column in web and parse(reg.get(key)) != web[column]]


def keeps_purchase(row, desde, quota_announcements_kept=frozenset()):
    """Con fecha de corte entran las compras desde esa fecha y, de antes, las validas sin remesar (Plano = f) y las de
    un anuncio cuyo cupo se carga (la web suma Kilos Entregados con ellas, RN-21.1.6)."""
    bought = parse_date(row.get("fecha_compra"))
    if desde is None or bought is None or bought >= desde:
        return True
    if str(parse_int(s(row, "anuncio"))) in quota_announcements_kept:
        return True
    return s(row, "plano") != "t" and s(row, "forma_de_pago") != "ANULADA"


def quota_purchase(row, quota_announcements_kept):
    """Regla del 18-nov: el saldo del cupo solo baja con las compras de cupo (compras_cafe_seco.con_cupo, V29). Access
    guardaba las de "Compras Cupos" en COMPRAS sin marca: las validas de un anuncio cuyo cupo se carga cuentan como de
    cupo, las mismas que resta pending_quotas, asi el saldo de la web es el del corte."""
    return (s(row, "forma_de_pago") != "ANULADA"
            and str(parse_int(s(row, "anuncio"))) in quota_announcements_kept)


def announcement_number(prefix, number):
    """Decision (a) del 18-nov: el anuncio como lo guarda la web, con el prefijo del punto ("SDTA-3490",
    AnnouncementServiceImpl.toResponse). Uno que ya trae prefijo (segunda corrida) o vacio se deja igual."""
    text = (number or "").strip()
    if not text or "-" in text:
        return text
    return f"{prefix}-{parse_int(text)}"


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
    """Anuncios de Access con cupo (Cupo > 0)."""
    return [row for row in rows if parse_decimal(row.get("cupo")) > 0]


def pending_quotas(announcements, purchases):
    """
    Decision (b) del 18-nov (se revierte P8): los cupos de anuncio con saldo el dia del corte, [(anuncio, saldo)].
    Saldo = Cupo - kilos netos de las compras validas con ese anuncio, como lo calcula la web (RN-21.1.6); el
    "Entregados" de Access no se usa (se actualizaba a mano con el macro "actualizar cupos").
    """
    delivered = {}
    for row in purchases:
        if s(row, "forma_de_pago") == "ANULADA":
            continue
        key = parse_int(s(row, "anuncio"))
        delivered[key] = delivered.get(key, Decimal(0)) + parse_decimal(row.get("kilos_netos"))
    pending = []
    for row in quota_announcements(announcements):
        balance = parse_decimal(row.get("cupo")) - delivered.get(parse_int(s(row, "anuncio")), Decimal(0))
        if balance > 0:
            pending.append((row, balance))
    return pending


def max_invoice(rows):
    """Mayor factura de COMPRAS de Access, tambien las anuladas (el consecutivo no se reutiliza)."""
    return max((parse_int(row.get("factura")) for row in rows), default=0)


# Un numero de remision que queda suelto arriba, a mas de esto del siguiente mas bajo, no es la numeracion normal
# del punto (CO1341 -> CO9999 en Consaca, 24-nov): no cuenta para el contador; su remesa conserva el numero.
MAX_REMISSION_GAP = 100


def normal_remission_last(numbers):
    """(ultimo numero de la numeracion normal, numeros sueltos ignorados). Se quitan desde arriba los numeros que
    saltan mas de MAX_REMISSION_GAP sobre el siguiente mas bajo; las series reales (en Access cada punto tiene una
    baja de RP y la alta de siempre) tienen muchos numeros seguidos y se conservan."""
    # ponytail: solo ve numeros sueltos; dos o mas anomalos seguidos (9998, 9999) contarian como serie real.
    ordered = sorted(set(numbers))
    ignored = []
    while len(ordered) > 1 and ordered[-1] - ordered[-2] > MAX_REMISSION_GAP:
        ignored.insert(0, ordered.pop())
    return (ordered[-1] if ordered else 0), ignored


def remission_numbers(rows):
    """{numero: (texto mas usado, compras, primera fecha, ultima fecha)} de la columna remision de COMPRAS."""
    seen = {}
    for row in rows:
        text = s(row, "remision")
        m = re.search(r"(\d+)$", text)
        if not m:
            continue
        entry = seen.setdefault(int(m.group(1)), {"texts": {}, "dates": []})
        entry["texts"][text] = entry["texts"].get(text, 0) + 1
        if parse_date(row.get("fecha_compra")):
            entry["dates"].append(parse_date(row.get("fecha_compra")))
    return {n: (max(e["texts"], key=e["texts"].get), sum(e["texts"].values()), min(e["dates"], default=None),
                max(e["dates"], default=None)) for n, e in seen.items()}


def remission_summary(rows):
    """Ultima remision por numero y por fecha (de su ultima compra), las ignoradas y el numero de la siguiente."""
    numbers = remission_numbers(rows)
    last, ignored = normal_remission_last(numbers)
    normal = [n for n in numbers if n not in ignored]
    by_date = max(normal, key=lambda n: (numbers[n][3] or date.min, n), default=None)
    return {
        "by_number": (numbers[last][0], numbers[last][3]) if last else None,
        "by_date": (numbers[by_date][0], numbers[by_date][3]) if by_date is not None else None,
        "ignored": [(numbers[n][0], numbers[n][1], numbers[n][2], numbers[n][3]) for n in ignored],
        "next": last + 1,
    }


def max_remission(rows):
    """Ultimo numero de remision de la numeracion normal de Access (TA0968 -> 968), sin los sueltos; 0 si no hay."""
    return normal_remission_last(remission_numbers(rows))[0]


def set_remission_counter(cur, agency_id, number):
    """Contador de remision/remesa de la agencia (V31: uno por agencia) en al menos `number`; nunca lo baja.
    Devuelve el contador que queda."""
    cur.execute("INSERT INTO consecutivos_remision_agencia (agencia_id, ultimo_consecutivo) VALUES (%(a)s, %(n)s)"
                " ON CONFLICT (agencia_id) DO UPDATE SET ultimo_consecutivo ="
                " GREATEST(consecutivos_remision_agencia.ultimo_consecutivo, EXCLUDED.ultimo_consecutivo)"
                " RETURNING ultimo_consecutivo", {"a": agency_id, "n": number})
    return cur.fetchone()[0]


def transaction_collisions(ids, prefix):
    """Id de Access con el formato de la web (PREFIJO + digito + codigo + 3 cifras, con o sin guiones: SDTA3SUM001
    o SDTA-3SUM-001): chocarian con un consecutivo."""
    pattern = re.compile(rf"^{re.escape(prefix)}-?\d[A-Z]{{0,3}}-?\d{{3}}$", re.IGNORECASE)
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


def load_control_record(cur, agency_id, user_id):
    reg = read_csv("regcontrol_migrar.csv")[0]
    columns = [column for _, column, _ in CONTROL_FIELDS]
    cur.execute(f"SELECT id, {', '.join(columns)} FROM registros_control WHERE agencia_id = %s AND activo",
                (agency_id,))
    existing = cur.fetchone()
    if existing:
        # 24-nov: Access manda en los campos que trae; se actualizan y quedan en el Historial.
        updates = control_record_updates(dict(zip(columns, existing[1:])), reg)
        print(f"  Registro de Control: ya existe en la web; se actualiza con Access. Diferencias: {len(updates)}")
        for column, web, access in updates:
            print(f"    {column}: base {web} / Access {access}")
        if updates:
            cur.execute(f"UPDATE registros_control SET {', '.join(f'{c} = %s' for c, _, _ in updates)} WHERE id = %s",
                        (*[access for _, _, access in updates], existing[0]))
            before, after = control_record_history(updates)
            cur.execute("INSERT INTO historial_cambios (fecha, usuario_id, entidad, entidad_id, accion, agencia_id,"
                        " antes, despues) VALUES (now(), %s, 'REGISTRO_CONTROL', %s, 'MODIFICAR', %s, %s, %s)",
                        (user_id, existing[0], agency_id, psycopg2.extras.Json(before), psycopg2.extras.Json(after)))
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
            note_stop("Asociado sin IdAsociado", row)
            continue

        # Sin agencia o sin apellido se migran igual (V34): Access los tiene asi, no se inventa nada.
        grower_type_value = grower_type(row)
        if grower_type_value is None:
            note_stop("Asociado sin tipo (C, F o S) en Access",
                      f"Id {s(row, 'id_access') or '?'}, cedula {id_number}, {s(row, '1er nombre')}"
                      f" {s(row, '1er apellido')}, punto {DATA_DIR.name}")
            continue
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
                grower_type_value,
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


# Anuncios (numero de Access) cuyo cupo se carga; sus compras entran aunque sean de antes del corte.
QUOTA_ANNOUNCEMENTS_KEPT = set()


def purchase_rows(desde):
    """Filas de COMPRAS que entran con la fecha de corte."""
    return [row for row in read_csv("compras_migrar.csv")
            if keeps_purchase(row, desde, QUOTA_ANNOUNCEMENTS_KEPT)]


def cheque_text(value):
    """Cheque de Access tal cual, como texto (25-nov, V34: la base acepta de 1 a 15 digitos; no se rellena con
    ceros); None si no hay."""
    number = parse_int(value)
    return str(number) if number > 0 else None


def payment_of(row):
    """Forma de pago y cheque de la compra (el desglose de pagos mixtos va en formas_pago_compras)."""
    if parse_decimal(row.get("fpch")) > 0:
        return "CHEQUE", cheque_text(row.get("numcheque"))
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
    prefix = agency_prefix(cur, agency_id)

    def common_values(row, invoice_number, grower):
        first_name, last_name, address, phone, grower_type = grower
        if not seller_last_name(last_name):
            note_discard("compra con caficultor sin apellido en Access (se carga con el apellido vacio)",
                         f"factura {invoice_number}, cedula {s(row, 'cedula')}")
        last_name = seller_last_name(last_name)
        payment_method, check_number = payment_of(row)
        return (
            parse_date(row["fecha_compra"]), invoice_number, agency_id, fund_by_code[s(row, "fondo")],
            s(row, "especial"), product_by_code[s(row, "cod_prod")], announcement_number(prefix, s(row, "anuncio")),
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
            note_stop("Compra cuyo caficultor no esta en Asociados", (invoice_number, s(row, "cedula")))
            continue
        bad = out_of_range(row)
        if bad:
            note_stop("Compra con un porcentaje que no cabe en la base",
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
                parse_decimal(row["castigo"]), quota_purchase(row, QUOTA_ANNOUNCEMENTS_KEPT)))

    psycopg2.extras.execute_values(
        cur,
        f"INSERT INTO compras_cafe_seco ({COMMON_COLUMNS}, peso_tot_alm, porc_merma, peso_tot_pasilla,"
        " porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion, castigo, con_cupo) VALUES %s",
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


# ---------- inventario (24-nov) ----------

def access_remission(row):
    """Texto de la remision de una fila del INVENTARIO de Access (columna "remision" con tilde)."""
    return next((s(row, key) for key in row if key.lower().startswith("remisi")), "")


def inventory_rows(rows):
    """(entradas, salidas) del INVENTARIO de Access tal cual: entrada = fila con factura (tambien las de 0 kg);
    salida = fila con remision (por codigo de producto, sin factura). Una fila sin ninguna de las dos, o con las
    dos, detiene la carga."""
    entries = [row for row in rows if s(row, "factura") and not access_remission(row)]
    exits = [row for row in rows if access_remission(row) and not s(row, "factura")]
    for row in rows:
        if bool(s(row, "factura")) == bool(access_remission(row)):
            note_stop("Fila de INVENTARIO que no es entrada ni salida",
                      f"Id {s(row, 'id_access') or '?'}, factura {s(row, 'factura')}, remision {access_remission(row)}")
    return entries, exits


def entry_balance(row, purchase):
    """Saldo disponible del movimiento para Registrar Salidas: sus kilos si la compra sigue sin remesar (Plano = f) y
    valida; 0 si ya salio en una remesa, si esta anulada o si no hay compra (saldo que viene del anio anterior)."""
    if purchase is None or s(purchase, "plano") == "t" or s(purchase, "forma_de_pago") != "VALIDA":
        return Decimal(0)
    return parse_decimal(row.get("kilos_netos"))


def opening_purchase_id(agency_id, invoice):
    """compra_id del saldo que viene del anio anterior (factura 9911210226, sin compra): negativo y unico por agencia."""
    return -(agency_id * 10 ** 12 + int(re.sub(r"\D", "", invoice) or 0))


def exit_remissions(exits):
    """{numero: {text, date, destino, lines}} de las salidas de Access, en orden de numero."""
    grouped = {}
    for row in exits:
        text = access_remission(row)
        match = re.search(r"(\d+)$", text)
        if not match:
            note_stop("Salida de INVENTARIO sin numero de remision", discard_detail(row, 0, row.get("cantidad")))
            continue
        remission = grouped.setdefault(int(match.group(1)),
                                       {"text": text, "date": None, "destino": s(row, "destino"), "lines": []})
        day = parse_date(row.get("fecha"))
        if day and (remission["date"] is None or day > remission["date"]):
            remission["date"] = day
        remission["lines"].append(row)
    return dict(sorted(grouped.items()))


def exit_remission_rows(exits):
    """Las salidas como filas de remision (texto y fecha), para el contador de remision."""
    return [{"remision": access_remission(row), "fecha_compra": s(row, "fecha")} for row in exits]


def load_inventory(cur, agency_id, user_id):
    """
    INVENTARIO de Access (24-nov): cada entrada a inventarios, unida a su compra por factura, con su saldo disponible
    (entry_balance); cada salida a una remision historica VIGENTE (numero de Access, sin remesa) con una linea por fila.
    Las salidas de Access no van por factura: cada linea apunta a la primera entrada de su codigo de producto, que
    solo sirve para el codigo. Asi el saldo y el valor promedio por codigo quedan como en Access. Idempotente.
    """
    entries, exits = inventory_rows(read_csv("inventario_migrar.csv"))
    access_purchases = {s(r, "factura"): r for r in read_csv("compras_migrar.csv")}
    web_purchases = purchases_by_invoice(cur, agency_id, "kilos_netos")
    cur.execute("SELECT codigo, id, fondo_id FROM productos")
    products = {code: (pid, fund) for code, pid, fund in cur.fetchall()}
    modules = {"DRY": "DRY_COFFEE", "HUSK": "HUSK"}

    inserted = opening = 0
    for row in entries:
        code, invoice = s(row, "cod_prod"), s(row, "factura")
        if code not in products:
            note_stop("Entrada de INVENTARIO con codigo de producto inexistente",
                         f"factura {invoice}, codigo {code}, kilos {s(row, 'kilos_netos')}")
            continue
        found = web_purchases.get(int(invoice)) if invoice.isdigit() and int(invoice) < 2 ** 31 else None
        if found:
            module, purchase_id, number = modules[found[2]], found[1], int(invoice)
            purchase = access_purchases.get(invoice)
        else:
            module, purchase_id, number, purchase = "DRY_COFFEE", opening_purchase_id(agency_id, invoice), 0, None
            opening += 1
        cur.execute(
            "INSERT INTO inventarios (modulo_compra, compra_id, agencia_id, producto_id, especial, numero_factura,"
            " fecha_compra, sacos, kilos_brutos, kilos_netos, saldo_kilos, factor, vr_inventario, creado_en)"
            " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
            " ON CONFLICT (modulo_compra, compra_id) DO NOTHING",
            (module, purchase_id, agency_id, products[code][0], s(row, "especial"), number,
             parse_date(row.get("fecha")), parse_int(row.get("sacos")), parse_decimal(row.get("kilos_brutos")),
             parse_decimal(row.get("kilos_netos")), entry_balance(row, purchase), parse_decimal(row.get("porcalmsana")),
             parse_decimal(row.get("vr_inventario")), parse_timestamp(s(row, "fecha"))))
        inserted += cur.rowcount
    print(f"  inventarios: {inserted} entradas de INVENTARIO ({opening} del saldo que viene del anio anterior).")

    cur.execute("SELECT DISTINCT ON (producto_id) producto_id, id FROM inventarios WHERE agencia_id = %s"
                " ORDER BY producto_id, fecha_compra, id", (agency_id,))
    first_entry = dict(cur.fetchall())
    cur.execute("SELECT numero_remision_anterior FROM remisiones WHERE agencia_id = %s", (agency_id,))
    existing = {r[0] for r in cur.fetchall()}
    remissions = lines = 0
    for number, remission in exit_remissions(exits).items():
        if number in existing:
            continue
        funds = {products[s(r, "cod_prod")][1] for r in remission["lines"] if s(r, "cod_prod") in products}
        cur.execute(
            "INSERT INTO remisiones (numero_remision_anterior, agencia_id, fecha_remision, destino, exportado,"
            " creado_por_usuario_id, creado_en, fondo_id, consecutivo, numero_remision, estado, de_access)"
            " VALUES (%s, %s, %s, %s, false, %s, %s, %s, %s, %s, 'VIGENTE', true) RETURNING id",
            (number, agency_id, remission["date"], remission["destino"] or None, user_id,
             parse_timestamp(s(remission["lines"][-1], "fecha")), funds.pop() if len(funds) == 1 else None, number,
             remission["text"]))
        remission_id = cur.fetchone()[0]
        remissions += 1
        for row in remission["lines"]:
            product = products.get(s(row, "cod_prod"), (None,))[0]
            if first_entry.get(product) is None:
                note_stop("Salida de INVENTARIO de un codigo sin entradas", discard_detail(row, 0, row.get("cantidad")))
                continue
            cur.execute(
                "INSERT INTO remisiones_detalle (remision_id, inventario_id, cantidad, valor_unitario, valor_salida,"
                " sacos, kilos_brutos, factor_salida) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
                (remission_id, first_entry[product], parse_decimal(row.get("cantidad")),
                 parse_decimal(row.get("valor unitario")), parse_decimal(row.get("vr_salida")),
                 parse_int(row.get("sacos")), parse_decimal(row.get("kilos_brutos")),
                 parse_decimal(row.get("porcalmsanasl"))))
            lines += 1
    print(f"  remisiones: {remissions} salidas de INVENTARIO de Access ({lines} lineas), sin remesa.")


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
        if purchase is None:
            continue  # movimiento manual de Caja: su marca se guarda en load_manual_cash_entries
        if parse_int(purchase[0], None) != check:
            note_stop("Rel_cheques de un pago de compra con otro numero de cheque",
                      f"Id {s(row, 'id_access') or '?'}, factura {invoice}, cheque Caja {check} / compra {purchase[0]}")
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
        check = cheque_text(row.get("cheque"))
        method = s(row, "forma_de_pago")
        table = "movimientos_caja"
        copy_key = (transaction_id, inflow if inflow > 0 else outflow)
        if check and supply_checks.get(copy_key, 0) > 0:
            supply_checks[copy_key] -= 1
            method = "CHEQUE"
            if outflow > 0:
                table = "suministros"
        if ledger_fund(row) is None:
            note_stop("Caja con un fondo que no es RP ni LF", discard_detail(row, inflow, outflow))
            continue
        entry_date = parse_date(s(row, "fecha"))
        ids = existing.get((table, transaction_id, entry_date, inflow, outflow, check))
        if ids:
            entry_id = ids.pop(0)
        else:
            cur.execute(
                f"INSERT INTO {table} (id_transaccion, agencia_id, fondo_id, fecha, cedula, detalle,"
                " entradas, salidas, forma_pago, numero_cheque, creado_en)"
                " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id",
                (transaction_id, agency_id, fund_by_code[ledger_fund(row)], entry_date, s(row, "cedula"),
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


# Fondos de la web. Un fondo de Access que no es uno de estos detiene la carga (25-nov: no se supone).
KNOWN_FUNDS = {"RP", "LF"}


def ledger_fund(row):
    """Codigo de fondo de un movimiento de Access tal cual; None si no es RP ni LF (detiene la carga)."""
    fund = s(row, "fondo")
    return fund if fund in KNOWN_FUNDS else None


def discard_detail(row, inflow, outflow):
    """Fecha, numero, detalle, fondo y valor de un movimiento descartado, para revisar si mueve un saldo."""
    day = parse_date(row.get("fecha"))
    return (f"Id {s(row, 'id_access') or '?'}, {day}, {s(row, 'id_transaccion')}, {s(row, 'detalle')},"
            f" fondo {s(row, 'fondo')},"
            f" entradas {inflow}, salidas {outflow}")


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
        fund = ledger_fund(row)
        if fund is None:
            note_stop("Suministros con un fondo que no es RP ni LF", discard_detail(row, inflow, outflow))
            continue
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("suministros"),
            (transaction_id, agency_id, fund_by_code[fund], entry_date, s(row, "cedula"),
             s(row, "detalle") or None, inflow, outflow, s(row, "forma_de_pago") or None,
             cheque_text(row.get("cheque")), parse_timestamp(s(row, "fecha"))))
    print(f"  suministros: {inserted} filas propias de Suministros.")

    found = existing("movimientos_caja_menor", "entradas, salidas")
    inserted = 0
    for row in read_csv("cajamenor_migrar.csv"):
        if before_cut(row, desde):
            continue
        transaction_id = s(row, "id_transaccion")
        inflow, outflow = parse_decimal(s(row, "entradas")), parse_decimal(s(row, "salidas"))
        if ledger_fund(row) is None:
            note_stop("CajaMenor con un fondo que no es RP ni LF", discard_detail(row, inflow, outflow))
            continue
        entry_date = parse_date(s(row, "fecha"))
        inserted += insert_once(
            found, (transaction_id, entry_date, inflow, outflow), ledger_sql.format("movimientos_caja_menor"),
            (transaction_id, agency_id, fund_by_code[ledger_fund(row)], entry_date, s(row, "cedula"),
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
            note_stop("Pago mixto sin compra migrada", invoice)
            continue
        if sum(parts) != purchase[0]:
            note_stop("Pago mixto que no suma el neto", (invoice, [str(p) for p in parts], str(purchase[0])))
            continue
        splits.append((purchase[2], purchase[1], *parts, cheque_text(s(row, "numcheque"))))
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
    prefix = agency_prefix(cur, agency_id)
    inserted = 0
    for row in load:
        number = announcement_number(prefix, s(row, "anuncio"))
        grower = growers.get(s(row, "cedula"))
        if grower is None:
            note_stop("Compra a futuro cuyo caficultor no esta en Asociados", (s(row, "anuncio"), s(row, "cedula")))
            continue
        cur.execute(
            "INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio, cedula, nombres,"
            " apellidos, tipo, direccion, especial, kilos, saldo, fecha_entrega, finca, municipio, vereda,"
            " pr_alm_sana, pr_alm_defec, bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id)"
            " SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s"
            " WHERE NOT EXISTS (SELECT 1 FROM compras_anunciadas WHERE agencia_id = %s AND numero_anuncio = %s"
            " AND cedula = %s)",
            (parse_timestamp(row["fecha_anuncio"]), agency_id, number, parse_date(row["fecha_anuncio"]),
             s(row, "cedula"), grower[0], grower[1], grower[2] or "C", grower[3] or "", s(row, "especial"),
             parse_decimal(row["kilos_anunciados"]), parse_decimal(row["saldo_kilos"]),
             parse_date(row["fecha_entrega"]), s(row, "finca") or None, s(row, "mpio") or None,
             s(row, "vereda") or None, parse_decimal(row["pr_almsana"]), parse_decimal(row["pr_almdefec"]),
             parse_decimal(row["bonificación"]), parse_decimal(row["costos"]), parse_decimal(row["vrinccalidad"]),
             parse_decimal(row["prbcps"]), admin_user_id, agency_id, number, s(row, "cedula")),
        )
        inserted += cur.rowcount
    print(f"  compras a futuro: {inserted} cargadas (saldo y entrega vigente); {len(expired)} vencidas no se cargan.")
    for row in expired:
        print(f"    vencida: anuncio {s(row, 'anuncio')}, cedula {s(row, 'cedula')}, saldo {s(row, 'saldo_kilos')} kg,"
              f" entrega {s(row, 'fecha_entrega')[:10]}")


def load_quota_announcements(cur, agency_id):
    """
    Decision (b) del 18-nov: a cupos_anuncios solo los cupos con saldo el dia del corte (pending_quotas). Se guarda el
    Cupo; Entregados y Saldo los calcula la web con las compras de cupo (con_cupo, quota_purchase). Los que ya no
    tienen saldo se listan y no se cargan.
    Idempotente (uq_cupos_anuncios_anuncio_agencia_id).
    """
    announcements = read_csv("anuncios_migrar.csv")
    pending = pending_quotas(announcements, read_csv("compras_migrar.csv"))
    pending_numbers = {parse_int(s(r, "anuncio")) for r, _ in pending}
    inserted = 0
    for row, balance in pending:
        number = parse_int(s(row, "anuncio"))
        cur.execute("SELECT id FROM anuncios_agencias WHERE agencia_id = %s AND numero_anuncio = %s", (agency_id, number))
        found = cur.fetchone()
        if not found:
            note_stop("Cupo de un anuncio que no se cargo", (number, s(row, "cupo")))
            continue
        QUOTA_ANNOUNCEMENTS_KEPT.add(str(number))
        cur.execute("INSERT INTO cupos_anuncios (anuncio_agencia_id, cupo) VALUES (%s, %s)"
                    " ON CONFLICT (anuncio_agencia_id) DO NOTHING", (found[0], parse_decimal(s(row, "cupo"))))
        inserted += cur.rowcount
        print(f"    anuncio {number} ({s(row, 'especial')}): cupo {s(row, 'cupo')}, saldo {balance}")
    print(f"  cupos de anuncio: {inserted} cargados (con saldo al corte).")
    for row in quota_announcements(announcements):
        if parse_int(s(row, "anuncio")) not in pending_numbers:
            print(f"    sin saldo, no se carga: anuncio {s(row, 'anuncio')} ({s(row, 'especial')}), cupo {s(row, 'cupo')}")


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

    # Remision y remesa: un solo contador por agencia (V31) que sigue la serie de Access (TA0968 -> SDTA-969).
    # Tambien las salidas de INVENTARIO: su numero de remision no puede volver a salir en la web.
    remission_rows = compras + exit_remission_rows(inventory_rows(read_csv("inventario_migrar.csv"))[1])
    access_remission = max_remission(remission_rows)
    summary = remission_summary(remission_rows)
    fixed = fixed_remission(CORRECTIONS)
    if fixed is not None:
        print(f"  remision: correcciones.csv fija el contador en {fixed} (la regla daba {access_remission});"
              f" la siguiente sale con {fixed + 1}")
        access_remission = fixed
    if summary["by_number"]:
        print(f"  remision: ultima por numero {summary['by_number'][0]} ({summary['by_number'][1]}), ultima por fecha"
              f" {summary['by_date'][0]} ({summary['by_date'][1]}); la siguiente sale con {access_remission + 1}")
    for text, count, first, last in summary["ignored"]:
        print(f"  remision {text} ignorada para el contador (numero suelto): {count} compras del {first} al {last};"
              " su remesa conserva el numero")
    cur.execute("SELECT ultimo_consecutivo FROM consecutivos_remision_agencia WHERE agencia_id = %s", (agency_id,))
    web = (cur.fetchone() or [None])[0]
    rows.append(("remision", access_remission, web, set_remission_counter(cur, agency_id, access_remission) + 1))

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

def backup(base, label="migrar_punto"):
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    target = ROOT / "docs" / "respaldos-bd" / f"{base}_antes_{label}_{stamp}.dump"
    with target.open("wb") as out:
        subprocess.run(["docker", "exec", "cafeoccidente-postgres", "pg_dump", "-U", "postgres", "-Fc", base],
                       check=True, stdout=out)
    return target


def print_excluded():
    """Registros de Access que no se cargaron porque correcciones.csv los excluye, con todos sus datos."""
    print(f"  corregidos por correcciones.csv: {len(CORRECTED)}")
    for (table, ident), (column, before, after) in CORRECTED.items():
        fix = CORRECTIONS[(table, ident)]
        print(f"    {table} Id {ident}: {column} '{before}' -> '{after}' ({s(fix, 'motivo')};"
              f" confirmo: {s(fix, 'confirmado_por') or 'PENDIENTE'})")
    print(f"  excluidos por correcciones.csv: {len(EXCLUDED)}")
    for (table, ident), row in EXCLUDED.items():
        fix = CORRECTIONS[(table, ident)]
        data = ", ".join(f"{k} {v}" for k, v in row.items() if v and k != "id_access")
        print(f"    {table} Id {ident} ({s(fix, 'motivo')}; confirmo: {s(fix, 'confirmado_por') or 'PENDIENTE'}): {data}")


def main():
    global DATA_DIR
    parser = argparse.ArgumentParser(description="Carga un punto de compra de Access (CSV) en la web.")
    parser.add_argument("--base", required=True, help="cafeoccidente_test, cafeoccidente u otra base local")
    parser.add_argument("--carpeta", default=str(DATA_DIR), help="carpeta con los *_migrar.csv del punto")
    parser.add_argument("--desde", type=date.fromisoformat, help="fecha de corte AAAA-MM-DD (opcional)")
    parser.add_argument("--aplicar", action="store_true", help="guarda los cambios (sin esto, modo prueba)")
    parser.add_argument("--correcciones", help="correcciones.csv (fuera del repo) con los registros a excluir")
    parser.add_argument("--definitiva", action="store_true",
                        help="carga definitiva: una correccion PENDIENTE detiene la carga")
    parser.add_argument("--admin-pruebas", action="store_true", help="crea admin_eltambo (base de pruebas)")
    parser.add_argument("--ness", action="store_true",
                        help="recarga cupos_ness con la NESS de esta base (borra la que se subio con HU-54)")
    args = parser.parse_args()
    DATA_DIR = Path(args.carpeta)
    load_corrections(args.correcciones, DATA_DIR.name, args.definitiva)

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
            cur.execute("SELECT id FROM usuarios WHERE usuario = 'admin'")
            admin_user_id = cur.fetchone()[0]
            load_control_record(cur, agency_id, admin_user_id)
            load_growers(cur)
            cur.execute("SELECT id FROM fondos WHERE codigo = 'RP'")
            rp_fund_id = cur.fetchone()[0]
            load_announcements(cur, agency_id, rp_fund_id)
            load_quota_announcements(cur, agency_id)
            load_purchases(cur, agency_id, admin_user_id, args.desde)
            load_inventory(cur, agency_id, admin_user_id)
            load_check_relation_marks(cur, agency_id)
            load_manual_cash_entries(cur, agency_id, args.desde)
            load_supplies_petty_cash_and_packaging(cur, agency_id, args.desde)
            load_purchase_payment_splits(cur, agency_id, args.desde)
            load_annulment_locks(cur, agency_id, args.desde)
            load_future_purchases(cur, agency_id, admin_user_id, datetime.now(ACCESS_ZONE).date())
            set_consecutives(cur, agency_id)
            if args.admin_pruebas:
                ensure_admin_eltambo(cur, agency_id)
            check_corrections_used()
            print_excluded()
            stop_if_problems()

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

    print("\nAvisos (se cargaron tal cual):")
    for reason, items in DISCARDED.items():
        print(f"  {reason}: {len(items)}")
        for item in items:
            print(f"    {item}")


if __name__ == "__main__":
    main()
