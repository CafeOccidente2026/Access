"""
Check de la tabla de agencias de migrate_eltambo.py (sin BD ni framework):
python scripts/test_equivalencias_agencias.py
"""

import csv
import re

from migrate_eltambo import AGENCY_ALIASES, DATA_DIR, EXTRA_AGENCIES, ROOT, canonical_access_value

SEED = ROOT / "cafeoccidente-backend" / "src" / "main" / "resources" / "db" / "migration" / "V2__datos_semilla.sql"


def seed_access_values():
    """nombre_access de las 20 agencias de la semilla (V2)."""
    agencies = SEED.read_text(encoding="utf-8").split("INSERT INTO agencias", 1)[1].split(";", 1)[0]
    return set(re.findall(r"\(\d+, '[^']*', \w+, '([^']*)'", agencies))


def main():
    seed = seed_access_values()
    assert len(seed) == 20, seed

    # Todo alias apunta a una agencia de la semilla o a una extra, y ningun alias es un valor real.
    for alias, target in AGENCY_ALIASES.items():
        assert target in seed or target in EXTRA_AGENCIES, (alias, target)
        assert alias not in seed and alias not in EXTRA_AGENCIES, alias
    assert not seed & EXTRA_AGENCIES.keys()

    # Resuelve alias, valores exactos y extras; un valor desconocido no tiene equivalencia.
    assert canonical_access_value("EL TAMB0", seed) == "EL TAMBO"
    assert canonical_access_value("Buesaco", seed) == "BUESACO1 OCCIDENTE"
    assert canonical_access_value("BUESACO2 OCCIDENTE", seed) == "BUESACO2"
    assert canonical_access_value("SANDONA", seed) == "SANDONA"
    assert canonical_access_value("TANGUA", seed) == "TANGUA"
    assert canonical_access_value("EL TAMBITO", seed) is None

    # Todo valor de Agencia del CSV de Access tiene equivalencia (docs/ es local: si falta, se salta).
    path = DATA_DIR / "asociados_migrar.csv"
    if path.exists():
        with open(path, encoding="utf-8") as f:
            values = {(row.get("agencia") or "").strip() for row in csv.DictReader(f)} - {"", "0"}
        unknown = sorted(v for v in values if canonical_access_value(v, seed) is None)
        assert not unknown, unknown
        print(f"asociados_migrar.csv: {len(values)} valores de Agencia, todos con equivalencia")
    else:
        print(f"FALTA {path}: se salta la revision del CSV")
    print("OK")


if __name__ == "__main__":
    main()
