package com.cafeoccidente.backend.supplies.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Tabla CajaMenor de Access (Caja Menor Suministros, Gastos Caja Menor). */
@Entity
@Table(name = "movimientos_caja_menor")
public class PettyCashEntry extends LedgerEntry {
}
