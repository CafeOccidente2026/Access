"""
Check de una base ya migrada con migrar_punto.py (sin framework; necesita la base):
python scripts/test_consecutivos_migracion.py --base <base> --carpeta <CSV del punto> [--comparar <otra base>]

1. Consecutivos: el siguiente numero de factura, de remision (RP y LF) y de anuncio de la agencia es el ultimo de
   Access + 1 (o mas, si la web ya habia usado numeros mayores).
2. Con --comparar (la misma carga hecha con y sin --desde): los saldos de la agencia dan igual en las dos bases:
   compras validas sin remesar (cantidad y kilos), compras a futuro con saldo, numeros de anuncio y Registro de
   Control.
"""

import argparse
import csv
import pathlib

from cargar_remesas_access import PURCHASES, agency_id, psql
from migrar_punto import max_invoice, max_remission, parse_int


def read(folder, name):
    with (folder / name).open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def check_consecutives(base, agency, folder):
    compras = read(folder, "compras_migrar.csv")
    invoice = int(psql(base, f"SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = {agency}"))
    assert invoice >= max_invoice(compras), (invoice, max_invoice(compras))

    remission = max_remission(compras)
    if remission:
        for fund in ("RP", "LF"):
            last = int(psql(base, "SELECT r.ultimo_consecutivo FROM consecutivos_remision r JOIN fondos f"
                                  f" ON f.id = r.fondo_id WHERE r.agencia_id = {agency} AND f.codigo = '{fund}'"))
            assert last >= remission, (fund, last, remission)

    announcement = max(parse_int(r["anuncio"]) for r in read(folder, "anuncios_migrar.csv"))
    last = int(psql(base, f"SELECT max(numero_anuncio) FROM anuncios_agencias WHERE agencia_id = {agency}"))
    assert last >= announcement, (last, announcement)
    print(f"  {base}: siguiente factura {invoice + 1} (Access {max_invoice(compras)}), remision {remission + 1},"
          f" anuncio {last + 1} (Access {announcement})")


BALANCES = {
    "compras sin remesar": "SELECT count(*), coalesce(sum(kilos_netos), 0) FROM ({p}) c"
                           " WHERE c.agencia_id = {a} AND c.remesa_id IS NULL AND c.estado = 'VALIDA'",
    "compras a futuro con saldo": "SELECT count(*), coalesce(sum(saldo), 0) FROM compras_anunciadas"
                                  " WHERE agencia_id = {a} AND saldo > 0",
    "anuncios": "SELECT count(*), max(numero_anuncio) FROM anuncios_agencias WHERE agencia_id = {a}",
    "registro de control": "SELECT count(*), max(prefijo || '/' || porc_aportes_asociados || '/' || tamano_muestra)"
                           " FROM registros_control WHERE agencia_id = {a} AND activo",
}


def balances(base, agency):
    return {name: psql(base, sql.format(p=PURCHASES, a=agency)) for name, sql in BALANCES.items()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--carpeta", required=True)
    parser.add_argument("--comparar")
    args = parser.parse_args()
    folder = pathlib.Path(args.carpeta)
    agency = agency_id(args.base, folder)

    check_consecutives(args.base, agency, folder)
    if args.comparar:
        mine, other = balances(args.base, agency), balances(args.comparar, agency_id(args.comparar, folder))
        for name in BALANCES:
            assert mine[name] == other[name], (name, mine[name], other[name])
            print(f"  {name}: {mine[name]} en las dos bases")
    print("OK")


if __name__ == "__main__":
    main()
