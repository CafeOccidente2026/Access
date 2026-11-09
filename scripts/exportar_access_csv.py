"""
Exporta las 14 tablas de una base de Access de un punto de compra a los CSV que carga la migracion
(los *_migrar.csv de docs/legacy-postgres-reference/eltambo/), con las mismas columnas y formatos.

Uso:  python scripts/exportar_access_csv.py <archivo.accdb> <carpeta_salida> [--clave <clave de la base>]

Abre la base en solo lectura con el driver ODBC de Access (no la modifica). La columna autonumerica "Id"
que agrega Access no va en los CSV. La carpeta de salida guarda datos reales: debe estar fuera de git.
"""

import argparse
import csv
import datetime as dt
from decimal import Decimal
from pathlib import Path

import pyodbc

# CSV -> tabla de Access.
TABLES = {
    "anuncios": "Anuncios",
    "asociados": "Asociados",
    "caja": "Caja",
    "cajamenor": "CajaMenor",
    "compras_a_futuro": "COMPRAS A FUTURO",
    "compras": "COMPRAS",
    "cupos": "CUPOS",
    "empaque": "Empaque",
    "inventario": "INVENTARIO",
    "ness": "NESS",
    "producto": "PRODUCTO",
    "rangosinc": "RangosInc",
    "regcontrol": "RegControl",
    "suministros": "Suministros",
}


def cell(value):
    """Texto de un valor de Access con el formato de los CSV de El Tambo."""
    if value is None:
        return ""
    if isinstance(value, bool):
        return "t" if value else "f"
    if isinstance(value, dt.datetime):
        return value.strftime("%Y-%m-%d %H:%M:%S")
    if isinstance(value, dt.date):
        return value.strftime("%Y-%m-%d 00:00:00")
    if isinstance(value, Decimal):
        # Moneda de Access (4 decimales): sin ceros de sobra, como la columna numeric de origen.
        return format(value.normalize(), "f") if value != value.to_integral() else str(int(value))
    if isinstance(value, float):
        return repr(value)
    return str(value)


def connect(accdb, password=None):
    conn = rf"DRIVER={{Microsoft Access Driver (*.mdb, *.accdb)}};DBQ={accdb};ReadOnly=1;"
    if password:
        conn += f"PWD={password};"
    return pyodbc.connect(conn, autocommit=True, readonly=True)


def export_table(cur, table, path):
    cur.execute(f"SELECT * FROM [{table}]")
    names = [d[0] for d in cur.description]
    keep = [i for i, name in enumerate(names) if name.lower() != "id"]
    count = 0
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow([names[i].lower() for i in keep])
        for row in cur:
            writer.writerow([cell(row[i]) for i in keep])
            count += 1
    return count


def export(accdb, out_dir, password=None):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    with connect(accdb, password) as cn:
        cur = cn.cursor()
        return {name: export_table(cur, table, out / f"{name}_migrar.csv") for name, table in TABLES.items()}


def main():
    parser = argparse.ArgumentParser(description="Exporta una base de Access a los CSV de la migracion.")
    parser.add_argument("accdb")
    parser.add_argument("carpeta")
    parser.add_argument("--clave", help="clave de la base de Access, si tiene")
    args = parser.parse_args()
    for name, count in export(args.accdb, args.carpeta, args.clave).items():
        print(f"  {name}_migrar.csv: {count} filas")


if __name__ == "__main__":
    main()
