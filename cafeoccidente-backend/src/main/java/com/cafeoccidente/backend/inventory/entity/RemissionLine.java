package com.cafeoccidente.backend.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Una línea de despacho contra una entrada de inventario puntual (Form_EXITS.bas,
 * Cantidad_AfterUpdate: {@code If Cantidad > Saldo Then MsgBox ... Else Vr_Salida = Valor_unitario
 * * Cantidad}). El legado guardaba esto como columnas planas en la fila de INVENTARIO (una sola
 * salida por entrada); acá se normaliza a esta tabla para soportar despachos parciales sin perder
 * historial - ver RemissionServiceImpl.
 */
@Entity
@Getter
@Setter
public class RemissionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "remission_id", nullable = false)
    private Remission remission;

    @ManyToOne
    @JoinColumn(name = "inventory_movement_id", nullable = false)
    private InventoryMovement inventoryMovement;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    /** Valor_unitario (ParaSalidas!VRUNIT - el Vr_Kilo/Vr_Inventario de la entrada origen). */
    @Column(name = "unit_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitValue;

    /** Vr_Salida = Valor_unitario * Cantidad. */
    @Column(name = "output_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal outputValue;
}
