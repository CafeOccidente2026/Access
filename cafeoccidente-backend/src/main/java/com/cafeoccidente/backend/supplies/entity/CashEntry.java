package com.cafeoccidente.backend.supplies.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Tabla Caja de Access (Caja suministros, Ajustes Caja). */
@Entity
@Table(name = "movimientos_caja")
public class CashEntry extends LedgerEntry {
}
