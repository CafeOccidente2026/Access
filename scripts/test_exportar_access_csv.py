"""
Check de exportar_access_csv.py (sin BD ni framework):
python scripts/test_exportar_access_csv.py [ruta de ElTambo2026.accdb]

1. Formato de cada tipo de valor de Access, igual al de los CSV de El Tambo.
2. Si esta la base de El Tambo (solo en local), la exporta y la compara con
   docs/legacy-postgres-reference/eltambo/: mismas columnas en las 14 tablas, y cada fila de los CSV sale
   igual en la exportacion, salvo en las columnas que cambiaron en Access despues de sacar esos CSV
   (CHANGED_AFTER_EXPORT). Las filas nuevas de Access se cuentan pero no fallan.
"""

import csv
import sys
import tempfile
from collections import Counter
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

from exportar_access_csv import TABLES, cell, export

ROOT = Path(__file__).resolve().parent.parent
ELTAMBO_CSV = ROOT / "docs" / "legacy-postgres-reference" / "eltambo"
DEFAULT_ACCDB = Path(r"C:\Users\Usuario\Documents\ElTambo2026.accdb")

# Columnas que se movieron en ElTambo2026.accdb despues de sacar los CSV (2026-11-08): remesas marcadas
# (exportado), Relacion Cheques (rel_cheques), anuncio 3416 sin entregados y PorcAsociados vaciado.
CHANGED_AFTER_EXPORT = {"exportado", "rel_cheques", "entregados", "porcasociados"}


def check_formats():
    assert cell(None) == ""
    assert cell(True) == "t" and cell(False) == "f"
    assert cell(datetime(2026, 1, 17, 0, 0)) == "2026-01-17 00:00:00"
    assert cell(date(2026, 1, 17)) == "2026-01-17 00:00:00"
    assert cell(Decimal("20562.0000")) == "20562"
    assert cell(Decimal("12.5000")) == "12.5"
    assert cell(97.10526315789474) == "97.10526315789474"
    assert cell(1.0) == "1.0"
    assert cell(43000) == "43000" and cell("EL TAMBO") == "EL TAMBO"


def read(path):
    with open(path, encoding="utf-8") as f:
        rows = list(csv.reader(f))
    return rows[0], rows[1:]


def same_except_changed(header, old, new):
    return all(o == n or h in CHANGED_AFTER_EXPORT for h, o, n in zip(header, old, new))


def check_eltambo(accdb):
    with tempfile.TemporaryDirectory() as out:
        export(str(accdb), out)
        for name in TABLES:
            old_header, old_rows = read(ELTAMBO_CSV / f"{name}_migrar.csv")
            new_header, new_rows = read(Path(out) / f"{name}_migrar.csv")
            assert old_header == new_header, (name, old_header, new_header)
            pending = Counter(map(tuple, new_rows))
            for row in map(tuple, old_rows):
                if pending[row]:
                    pending[row] -= 1
                    continue
                match = next((r for r in pending if pending[r] and same_except_changed(old_header, row, r)), None)
                assert match is not None, (name, row)
                pending[match] -= 1
            print(f"  {name}: {len(old_rows)} filas iguales, {sum(pending.values())} nuevas en Access")


def main():
    check_formats()
    accdb = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_ACCDB
    if accdb.exists():
        check_eltambo(accdb)
    else:
        print(f"  (sin {accdb}: solo se revisan los formatos)")
    print("OK")


if __name__ == "__main__":
    main()
