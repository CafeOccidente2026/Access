"""
Borra compras de prueba del navegador en cafeoccidente_test (nunca en otra base).

Uso: python scripts/borrar_compras_prueba.py --seco 1350 --pasilla 47 [--aplicar]

Antes de borrar hace `pg_dump -Fc` de cafeoccidente_test en docs/respaldos-bd/. Borra, en una sola
transaccion, la compra, sus formas de pago y su fila de inventario (las filas que crea el guardado;
cuentas_por_cobrar solo existe si hubo datafono). El consecutivo de factura no se devuelve.
Sin --aplicar solo muestra lo que borraria.
"""
import argparse
import datetime
import pathlib
import subprocess

CONTAINER = "cafeoccidente-postgres"
DATABASE = "cafeoccidente_test"
# modulo: (tabla, origen en formas_pago_compras, modulo en inventarios y cuentas_por_cobrar)
MODULES = {
    "seco": ("compras_cafe_seco", "DRY", "DRY_COFFEE"),
    "verde": ("compras_cafe_verde", "GREEN", "GREEN_COFFEE"),
    "pasilla": ("compras_pasilla", "HUSK", "HUSK"),
}


def psql(sql: str) -> str:
    cmd = ["docker", "exec", CONTAINER, "psql", "-U", "postgres", "-d", DATABASE, "-At", "-v", "ON_ERROR_STOP=1", "-c", sql]
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.strip()


def backup() -> pathlib.Path:
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M")
    target = pathlib.Path(__file__).resolve().parent.parent / "docs" / "respaldos-bd" / f"{DATABASE}_antes_borrar_compras_prueba_{stamp}.dump"
    with target.open("wb") as out:
        subprocess.run(["docker", "exec", CONTAINER, "pg_dump", "-U", "postgres", "-Fc", DATABASE], check=True, stdout=out)
    return target


def statements(module: str, ids: list[int]) -> list[str]:
    table, origin, inventory = MODULES[module]
    id_list = ",".join(str(int(i)) for i in ids)
    return [
        f"DELETE FROM formas_pago_compras WHERE origen = '{origin}' AND origen_id IN ({id_list})",
        f"DELETE FROM inventarios WHERE modulo_compra = '{inventory}' AND compra_id IN ({id_list})",
        f"DELETE FROM cuentas_por_cobrar WHERE modulo_compra = '{origin}' AND compra_id IN ({id_list})",
        f"DELETE FROM {table} WHERE id IN ({id_list})",
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    for module in MODULES:
        parser.add_argument(f"--{module}", type=int, nargs="*", default=[])
    parser.add_argument("--aplicar", action="store_true")
    args = parser.parse_args()

    sql = [s for m in MODULES if getattr(args, m) for s in statements(m, getattr(args, m))]
    if not sql:
        parser.error("indique al menos una compra")
    for s in sql:
        print(s)
    if not args.aplicar:
        print("Sin --aplicar: no se borro nada.")
        return
    print(f"Respaldo: {backup()}")
    print(psql("BEGIN; " + "; ".join(sql) + "; COMMIT;"))


if __name__ == "__main__":
    main()
