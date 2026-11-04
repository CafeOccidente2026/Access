"""
Check de las horas de migrate_eltambo.py (sin BD ni framework):
python scripts/test_fechas_migracion.py

Access guarda la hora local de Colombia. Al migrar, creado_en (timestamptz) de compras, Caja, Caja Menor,
Suministros y Empaques debe quedar en esa hora y no leida como UTC (asi paso con El Tambo: 5 h antes).
"""

from datetime import datetime, timedelta, timezone

from psycopg2.extensions import adapt

from migrate_eltambo import parse_timestamp


def main():
    midnight = parse_timestamp("2026-01-17 00:00:00")
    # El mismo instante que las 05:00 UTC: en hora de Colombia sigue siendo el 17/01 a las 00:00.
    assert midnight == datetime(2026, 1, 17, 5, 0, tzinfo=timezone.utc), midnight
    assert midnight.utcoffset() == timedelta(hours=-5)
    assert parse_timestamp("2026-08-27 11:51:59") == datetime(2026, 8, 27, 16, 51, 59, tzinfo=timezone.utc)
    # Solo fecha (Caja, Suministros, Empaques): medianoche de Colombia.
    assert parse_timestamp("2026-01-29") == datetime(2026, 1, 29, 5, 0, tzinfo=timezone.utc)
    assert parse_timestamp("") is None and parse_timestamp(None) is None and parse_timestamp("ayer") is None

    # Lo que psycopg2 manda a PostgreSQL lleva el desfase: no depende de la zona del servidor.
    assert b"-05:00" in adapt(midnight).getquoted(), adapt(midnight).getquoted()
    print("OK")


if __name__ == "__main__":
    main()
