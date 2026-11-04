"""
Pone o devuelve a 0 la muestra de café verde del Registro de Control de una agencia, solo en
cafeoccidente_test (nunca en otra base), para probar Compras Café Verde en el navegador.

Uso:
  python scripts/muestra_verde_prueba.py --poner [--agencia 1] [--aplicar]   # 140 / 12,5 / 186,18
  python scripts/muestra_verde_prueba.py --cero  [--agencia 1] [--aplicar]   # 0 / 0 / 0

Antes de cambiar hace `pg_dump -Fc` de cafeoccidente_test en docs/respaldos-bd/. Toca solo
peso_tot_alm_cv, peso_tot_pasilla_cv y peso_alm_sana_cv del registro activo de la agencia.
Sin --aplicar solo muestra lo que haría.
"""
import argparse
import datetime
import pathlib
import subprocess

CONTAINER = "cafeoccidente-postgres"
DATABASE = "cafeoccidente_test"
COLUMNS = ("peso_tot_alm_cv", "peso_tot_pasilla_cv", "peso_alm_sana_cv")
SAMPLE = ("140", "12.5", "186.18")
ZERO = ("0", "0", "0")


def psql(sql: str) -> str:
    cmd = ["docker", "exec", CONTAINER, "psql", "-U", "postgres", "-d", DATABASE, "-At", "-v", "ON_ERROR_STOP=1", "-c", sql]
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.strip()


def backup() -> pathlib.Path:
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    target = pathlib.Path(__file__).resolve().parent.parent / "docs" / "respaldos-bd" / f"{DATABASE}_antes_muestra_verde_{stamp}.dump"
    with target.open("wb") as out:
        subprocess.run(["docker", "exec", CONTAINER, "pg_dump", "-U", "postgres", "-Fc", DATABASE], check=True, stdout=out)
    return target


def current(agency: int) -> str:
    return psql(f"SELECT {', '.join(COLUMNS)} FROM registros_control WHERE agencia_id = {agency} AND activo")


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--poner", action="store_true")
    mode.add_argument("--cero", action="store_true")
    parser.add_argument("--agencia", type=int, default=1)
    parser.add_argument("--aplicar", action="store_true")
    args = parser.parse_args()

    values = SAMPLE if args.poner else ZERO
    assignments = ", ".join(f"{c} = {v}" for c, v in zip(COLUMNS, values))
    sql = f"UPDATE registros_control SET {assignments} WHERE agencia_id = {args.agencia} AND activo"
    print(f"Antes ({', '.join(COLUMNS)}): {current(args.agencia)}")
    print(sql)
    if not args.aplicar:
        print("Sin --aplicar: no se cambio nada.")
        return
    print(f"Respaldo: {backup()}")
    print(psql(sql))
    print(f"Despues: {current(args.agencia)}")


if __name__ == "__main__":
    main()
