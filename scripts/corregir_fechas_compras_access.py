"""
Corrige creado_en de las compras de cafe seco migradas de Access de un punto (por defecto El Tambo).

migrate_eltambo.py (hoy migrar_punto.py, que ya guarda la hora de Colombia) guardo la fecha y hora de Access (hora de Colombia) como si fuera UTC: la compra de las
00:00 del 17/01 quedo como 16/01 19:00 en hora local (ParaOperaciones de la remesa la muestra asi). Este script
la deja en hora de Colombia: creado_en = (creado_en en UTC) leido como hora de America/Bogota (+5 h).

Solo toca las compras de la agencia del punto cuya factura esta en su compras_migrar.csv y cuyo creado_en es exactamente la
fecha_compra de Access leida como UTC (las migradas sin corregir). Correrlo otra vez no cambia nada.

Uso:
  python scripts/corregir_fechas_compras_access.py --base cafeoccidente_test             # modo prueba
  python scripts/corregir_fechas_compras_access.py --base cafeoccidente_test --aplicar   # respaldo y cambio
  ... --carpeta <CSV del punto>   (la agencia sale de su regcontrol_migrar.csv)
"""
import argparse
import csv
import datetime
import pathlib
import subprocess

from cargar_remesas_access import agency_id

ROOT = pathlib.Path(__file__).resolve().parent.parent
FOLDER = ROOT / "docs" / "legacy-postgres-reference" / "eltambo"
CONTAINER = "cafeoccidente-postgres"
ZONE = "America/Bogota"


def access_times(rows):
    """{factura: 'AAAA-MM-DD HH:MM:SS'} de Access (hora de Colombia)."""
    return {int(r["factura"]): r["fecha_compra"][:19] for r in rows}


def values_sql(times):
    return ",".join(f"({invoice}, '{moment}'::timestamp)" for invoice, moment in sorted(times.items()))


def psql(base, sql):
    # Por la entrada estandar: con las 904 facturas, la linea de comandos de Windows es demasiado larga.
    cmd = ["docker", "exec", "-i", CONTAINER, "psql", "-U", "postgres", "-d", base, "-At", "-F", "|",
           "-v", "ON_ERROR_STOP=1"]
    return subprocess.run(cmd, input=sql, check=True, capture_output=True, text=True).stdout.strip()


def backup(base):
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    target = ROOT / "docs" / "respaldos-bd" / f"{base}_antes_fechas_compras_access_{stamp}.dump"
    with target.open("wb") as out:
        subprocess.run(["docker", "exec", CONTAINER, "pg_dump", "-U", "postgres", "-Fc", base], check=True, stdout=out)
    return target


def matches(agency, times):
    """Compras migradas aun sin corregir: la agencia, factura de Access y creado_en = hora de Access en UTC."""
    return (f"FROM compras_cafe_seco p"
            f" JOIN (VALUES {values_sql(times)}) AS acc (factura, momento) ON acc.factura = p.numero_factura"
            f" WHERE p.agencia_id = {agency} AND p.creado_en = acc.momento AT TIME ZONE 'UTC'")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--carpeta", default=str(FOLDER))
    parser.add_argument("--aplicar", action="store_true")
    args = parser.parse_args()
    folder = pathlib.Path(args.carpeta)
    agency = agency_id(args.base, folder)

    with (folder / "compras_migrar.csv").open(encoding="utf-8") as f:
        times = access_times(list(csv.DictReader(f)))
    where = matches(agency, times)
    pending = psql(args.base, f"SELECT count(*) {where}")
    total = psql(args.base, f"SELECT count(*) FROM compras_cafe_seco WHERE agencia_id = {agency}")
    print(f"Access: {len(times)} compras. En {args.base}: {total} compras de cafe seco de la agencia {agency},"
          f" {pending} migradas con la hora sin corregir.")
    sample = psql(args.base, f"SELECT p.numero_factura, to_char(p.creado_en AT TIME ZONE '{ZONE}', 'YYYY-MM-DD HH24:MI'),"
                             f" to_char(acc.momento, 'YYYY-MM-DD HH24:MI') {where} ORDER BY p.numero_factura LIMIT 3")
    for line in sample.splitlines():
        invoice, now, then = line.split("|")
        print(f"  factura {invoice}: hoy {now} hora Colombia -> {then}")
    if not args.aplicar:
        print("Modo prueba (sin --aplicar): no se cambio nada.")
        return
    print(f"Respaldo: {backup(args.base)}")
    changed = psql(args.base, f"UPDATE compras_cafe_seco p SET creado_en = acc.momento AT TIME ZONE '{ZONE}'"
                              f" FROM (VALUES {values_sql(times)}) AS acc (factura, momento)"
                              f" WHERE p.agencia_id = {agency} AND acc.factura = p.numero_factura"
                              " AND p.creado_en = acc.momento AT TIME ZONE 'UTC'")
    print(f"{changed}. Quedan sin corregir: {psql(args.base, f'SELECT count(*) {where}')}")


if __name__ == "__main__":
    main()
