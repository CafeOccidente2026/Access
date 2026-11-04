"""
Fase 7d: carga el historico de remesas de Access de El Tambo (COMPRAS.Plano / COMPRAS.Remision).

Uso:
  python scripts/cargar_remesas_access.py --base cafeoccidente_test             # modo prueba: solo muestra
  python scripts/cargar_remesas_access.py --base cafeoccidente_test --aplicar   # respaldo y carga

Lee docs/legacy-postgres-reference/eltambo/compras_migrar.csv. Por cada numero de Access (TA0436...) crea una
remesa VIGENTE de El Tambo con ese numero, su fondo, sin consecutivo ni empaques, creada por el usuario `admin`
y con fecha = la de su ultima compra (Access no guarda la fecha de la remesa). Despues marca sus compras de
cafe seco (remesa_id). Las compras con Plano = f quedan pendientes.

Antes de cambiar valida contra la base: cada factura existe en compras_cafe_seco de El Tambo, con el mismo
fondo, VALIDA, con kilos y sin remesa; y ningun numero existe ya en remesas. Si algo no cuadra no toca nada.
Con --aplicar hace `pg_dump -Fc` de la base en docs/respaldos-bd/ y carga todo en una sola transaccion.
"""
import argparse
import csv
import datetime
import pathlib
import subprocess
from collections import OrderedDict

ROOT = pathlib.Path(__file__).resolve().parent.parent
CSV = ROOT / "docs" / "legacy-postgres-reference" / "eltambo" / "compras_migrar.csv"
CONTAINER = "cafeoccidente-postgres"
BASES = ("cafeoccidente_test", "cafeoccidente")
AGENCY = "El Tambo"
CREATOR = "admin"


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


def check_database(base, plan):
    """Lista de problemas: cada factura remesada debe estar en El Tambo, igual fondo, VALIDA, con kilos y libre."""
    invoices = [i for r in plan.values() for i in r["facturas"]]
    found = {}
    for line in psql(base, "SELECT p.numero_factura, f.codigo, p.estado, p.kilos_netos > 0, p.remesa_id IS NULL"
                           " FROM compras_cafe_seco p JOIN agencias a ON a.id = p.agencia_id"
                           " JOIN fondos f ON f.id = p.fondo_id"
                           f" WHERE a.nombre = '{AGENCY}' AND p.numero_factura IN ({','.join(map(str, invoices))})"
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
    existing = psql(base, f"SELECT r.numero_remesa FROM remesas r JOIN agencias a ON a.id = r.agencia_id"
                          f" WHERE a.nombre = '{AGENCY}' AND r.numero_remesa IN ({quoted})")
    if existing:
        problems.append("ya existen las remesas: " + existing.replace("\n", ", "))
    return problems


def load_sql(plan):
    """Una transaccion: crea cada remesa y marca sus compras."""
    statements = ["BEGIN"]
    for number, r in plan.items():
        statements.append(
            "WITH nueva AS (INSERT INTO remesas (agencia_id, fondo_id, numero_remesa, estado, creado_por_usuario_id,"
            " creado_en) SELECT a.id, f.id, " f"'{number}', 'VIGENTE', u.id, '{r['fecha']}'::timestamp AT TIME ZONE"
            " 'America/Bogota' FROM agencias a, fondos f, usuarios u"
            f" WHERE a.nombre = '{AGENCY}' AND f.codigo = '{r['fondo']}' AND u.usuario = '{CREATOR}' RETURNING id,"
            " agencia_id) UPDATE compras_cafe_seco p SET remesa_id = nueva.id FROM nueva"
            f" WHERE p.agencia_id = nueva.agencia_id AND p.numero_factura IN ({','.join(map(str, r['facturas']))})"
            " AND p.remesa_id IS NULL AND p.estado = 'VALIDA'")
    statements.append("COMMIT")
    return "; ".join(statements) + ";"


def summary(base):
    return psql(base, "SELECT count(DISTINCT r.id), count(p.id),"
                      " (SELECT count(*) FROM compras_cafe_seco c JOIN agencias x ON x.id = c.agencia_id"
                      f"   WHERE x.nombre = '{AGENCY}' AND c.remesa_id IS NULL)"
                      " FROM remesas r JOIN agencias a ON a.id = r.agencia_id"
                      " LEFT JOIN compras_cafe_seco p ON p.remesa_id = r.id"
                      f" WHERE a.nombre = '{AGENCY}' AND r.consecutivo IS NULL")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", choices=BASES, required=True)
    parser.add_argument("--aplicar", action="store_true")
    args = parser.parse_args()

    with CSV.open(encoding="utf-8") as f:
        plan, pending = plan_remittances(list(csv.DictReader(f)))
    marked = sum(len(r["facturas"]) for r in plan.values())
    print(f"CSV de Access: {len(plan)} remesas ({min(plan)} a {max(plan)}), {marked} compras remesadas,"
          f" {len(pending)} sin remesar.")
    for fund in sorted({r["fondo"] for r in plan.values()}):
        numbers = [n for n, r in plan.items() if r["fondo"] == fund]
        print(f"  {fund}: {len(numbers)} remesas, {sum(len(plan[n]['facturas']) for n in numbers)} compras")

    problems = check_database(args.base, plan)
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
    psql(args.base, load_sql(plan))
    remittances, purchases, free = summary(args.base).split("|")
    print(f"Cargado en {args.base}: {remittances} remesas de Access con {purchases} compras;"
          f" {free} compras de cafe seco de {AGENCY} quedan sin remesa.")


if __name__ == "__main__":
    main()
