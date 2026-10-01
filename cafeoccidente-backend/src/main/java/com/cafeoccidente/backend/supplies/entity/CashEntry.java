package com.cafeoccidente.backend.supplies.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Tabla Caja de Access (Caja suministros, Ajustes Caja). */
@Entity
@Table(name = "cash_entry")
public class CashEntry extends LedgerEntry {
}
