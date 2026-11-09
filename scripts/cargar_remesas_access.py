"""
Fase 7d y 6c: carga el historico de remesas de Access de un punto (COMPRAS.Plano / COMPRAS.Remision).

Uso:
  python scripts/cargar_remesas_access.py --base cafeoccidente_test             # modo prueba: solo muestra
  python scripts/cargar_remesas_access.py --base cafeoccidente_test --aplicar   # respaldo y carga
  ... --carpeta <CSV del punto> (por defecto El Tambo)   ... --desde AAAA-MM-DD (la misma de migrar_punto.py)

Lee compras_migrar.csv del punto; la agencia sale de su regcontrol_migrar.csv. Por cada numero de Access
(TA0436...) crea una remesa VIGENTE con ese numero, su fondo, sin consecutivo ni empaques, creada por el usuario
`admin` y con fecha = la de su ultima compra (Access no guarda la fecha de la remesa). Despues marca sus compras
de cafe seco y de pasilla (remesa_id). Las compras con Plano = f quedan pendientes. Con --desde solo cuentan las
compras que migrar_punto.py carga con esa fecha.

Antes de cambiar valida contra la base: cada factura existe en las compras de la agencia, con el mismo fondo,
VALIDA, con kilos y sin remesa; y ningun numero existe ya en remesas. Si algo no cuadra no toca nada.
Con --aplicar hace `pg_dump -Fc` de la base en docs/respaldos-bd/ y carga todo en una sola transaccion.
"""
import argparse
import csv
import datetime
import pathlib
import subprocess
from collections import OrderedDict

from migrar_punto import canonical_access_value, keeps_purchase

ROOT = pathlib.Path(__file__).resolve().parent.parent
FOLDER = ROOT / "docs" / "legacy-postgres-reference" / "eltambo"
CONTAINER = "cafeoccidente-postgres"
CREATOR = "admin"
# Compras que puede tener una remesa de Access: la pasilla de Access va a compras_pasilla (6c).
PURCHASES = ("SELECT agencia_id, numero_factura, fondo_id, estado, kilos_netos, remesa_id FROM compras_cafe_seco"
             " UNION ALL SELECT agencia_id, numero_factura, fondo_id, estado, kilos_netos, remesa_id"
             " FROM compras_pasilla")


def agency_id(base, folder):
    """Id de la agencia del punto, por el nombre de su RegControl (como migrar_punto.py)."""
    with (folder / "regcontrol_migrar.csv").open(encoding="utf-8") as f:
        raw = next(csv.DictReader(f))["agencia"].strip()
    known = dict(line.split("|") for line in psql(base, "SELECT nombre_access, id FROM agencias"
                                                         " WHERE nombre_access IS NOT NULL").splitlines())
    target = canonical_access_value(raw, known)
    if target not in known:
        raise SystemExit(f"La agencia '{raw}' no existe en {base}.")
    return int(known[target])


def plan_remittances(rows):
    """{numero: {fondo, fecha, facturas}} en orden de numero, y las facturas pendientes (Plano = f)."""
    plan, pending, seen = {}, [], set()
    for r in rows:
        invoice = int(r["factura"])
        if invoice in seen:
            raise ValueError(f"factura {invoice} repetida en el CSV")
        seen.add(invoice)
        if r["plano"] != "t":
            pending.append(invoice)
            continue
        number = r["remision"].strip()
        if not number:
            raise ValueError(f"factura {invoice} remesada sin numero de remesa")
        if r["forma_de_pago"] != "VALIDA":
            raise ValueError(f"factura {invoice} anulada dentro de la remesa {number}")
        if float(r["kilos_netos"] or 0) <= 0:
            raise ValueError(f"factura {invoice} sin kilos dentro de la remesa {number}")
        remittance = plan.setdefault(number, {"fondo": r["fondo"], "fecha": r["fecha_compra"], "facturas": []})
        if remittance["fondo"] != r["fondo"]:
            raise ValueError(f"la remesa {number} mezcla fondos")
        remittance["fecha"] = max(remittance["fecha"], r["fecha_compra"])
        remittance["facturas"].append(invoice)
    for remittance in plan.values():
        remittance["facturas"].sort()
    return OrderedDict(sorted(plan.items())), sorted(pending)


def psql(base, sql):
    cmd = ["docker", "exec", CONTAINER, "psql", "-U", "postgres", "-d", base, "-At", "-F", "|",
           "-v", "ON_ERROR_STOP=1", "-c", sql]
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.strip()


def backup(base):
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    target = ROOT / "docs" / "respaldos-bd" / f"{base}_antes_remesas_access_{stamp}.dump"
    with target.open("wb") as out:
        subprocess.run(["docker", "exec", CONTAINER, "pg_dump", "-U", "postgres", "-Fc", base], check=True, stdout=out)
    return target


def check_database(base, agency, plan):
    """Lista de problemas: cada factura remesada debe estar en la agencia, igual fondo, VALIDA, con kilos y libre."""
    invoices = [i for r in plan.values() for i in r["facturas"]]
    found = {}
    for line in psql(base, "SELECT p.numero_factura, f.codigo, p.estado, p.kilos_netos > 0, p.remesa_id IS NULL"
                           f" FROM ({PURCHASES}) p JOIN fondos f ON f.id = p.fondo_id"
                           f" WHERE p.agencia_id = {agency} AND p.numero_factura IN ({','.join(map(str, invoices))})"
                     ).splitlines():
        invoice, fund, state, kilos, free = line.split("|")
        found[int(invoice)] = (fund, state, kilos == "t", free == "t")
    problems = []
    for number, r in plan.items():
        for invoice in r["facturas"]:
            got = found.get(invoice)
            if got is None:
                problems.append(f"{number}: factura {invoice} no esta en la base")
            elif got != (r["fondo"], "VALIDA", True, True):
                problems.append(f"{number}: factura {invoice} no cuadra (fondo, estado, kilos, libre) = {got}")
    quoted = ",".join(f"'{n}'" for n in plan)
    existing = psql(base, f"SELECT numero_remesa FROM remesas WHERE agencia_id = {agency}"
                          f" AND numero_remesa IN ({quoted})")
    if existing:
        problems.append("ya existen las remesas: " + existing.replace("\n", ", "))
    return problems


def load_sql(agency, plan):
    """Una transaccion: crea cada remesa y marca sus compras de seco y de pasilla."""
    statements = ["BEGIN"]
    for number, r in plan.items():
        invoices = ",".join(map(str, r["facturas"]))
        statements.append(
            "CREATE TEMP TABLE nueva ON COMMIT DROP AS WITH ins AS (INSERT INTO remesas (agencia_id, fondo_id,"
            " numero_remesa, estado, creado_por_usuario_id, creado_en) SELECT"
            f" {agency}, f.id, '{number}', 'VIGENTE', u.id, '{r['fecha']}'::timestamp AT TIME ZONE 'America/Bogota'"
            f" FROM fondos f, usuarios u WHERE f.codigo = '{r['fondo']}' AND u.usuario = '{CREATOR}' RETURNING id)"
            " SELECT id FROM ins")
        for table in ("compras_cafe_seco", "compras_pasilla"):
            statements.append(
                f"UPDATE {table} p SET remesa_id = nueva.id FROM nueva WHERE p.agencia_id = {agency}"
                f" AND p.numero_factura IN ({invoices}) AND p.remesa_id IS NULL AND p.estado = 'VALIDA'")
        statements.append("DROP TABLE nueva")
    statements.append("COMMIT")
    return "; ".join(statements) + ";"


def summary(base, agency):
    return psql(base, "SELECT count(DISTINCT r.id), count(p.agencia_id),"
                      f" (SELECT count(*) FROM ({PURCHASES}) c WHERE c.agencia_id = {agency} AND c.remesa_id IS NULL)"
                      f" FROM remesas r LEFT JOIN ({PURCHASES}) p ON p.remesa_id = r.id"
                      f" WHERE r.agencia_id = {agency} AND r.consecutivo IS NULL")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--carpeta", default=str(FOLDER))
    parser.add_argument("--desde", type=datetime.date.fromisoformat)
    parser.add_argument("--aplicar", action="store_true")
    args = parser.parse_args()
    folder = pathlib.Path(args.carpeta)
    agency = agency_id(args.base, folder)

    with (folder / "compras_migrar.csv").open(encoding="utf-8") as f:
        rows = [r for r in csv.DictReader(f) if keeps_purchase(r, args.desde)]
    plan, pending = plan_remittances(rows)
    if not plan:
        print(f"CSV de Access: no hay remesas que cargar; {len(pending)} compras sin remesar.")
        return
    marked = sum(len(r["facturas"]) for r in plan.values())
    print(f"CSV de Access: {len(plan)} remesas ({min(plan)} a {max(plan)}), {marked} compras remesadas,"
          f" {len(pending)} sin remesar.")
    for fund in sorted({r["fondo"] for r in plan.values()}):
        numbers = [n for n, r in plan.items() if r["fondo"] == fund]
        print(f"  {fund}: {len(numbers)} remesas, {sum(len(plan[n]['facturas']) for n in numbers)} compras")

    problems = check_database(args.base, agency, plan)
    if problems:
        print(f"No cuadra con {args.base} ({len(problems)}):")
        for p in problems[:30]:
            print("  " + p)
        raise SystemExit(1)
    print(f"{args.base}: las {marked} facturas existen, con su fondo, VALIDAS, con kilos y sin remesa.")
    if not args.aplicar:
        print("Modo prueba (sin --aplicar): no se cambio nada.")
        return
    print(f"Respaldo: {backup(args.base)}")
    psql(args.base, load_sql(agency, plan))
    remittances, purchases, free = summary(args.base, agency).split("|")
    print(f"Cargado en {args.base}: {remittances} remesas de Access con {purchases} compras;"
          f" {free} compras de la agencia quedan sin remesa.")


if __name__ == "__main__":
    main()
