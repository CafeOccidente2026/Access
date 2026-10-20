package com.cafeoccidente.backend.supplies.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Tabla Suministros de Access (Ingresar Suministros, Cheques Girados). */
@Entity
@Table(name = "suministros")
public class SupplyEntry extends LedgerEntry {
}
