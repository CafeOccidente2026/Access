package com.cafeoccidente.backend.supplies.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Columnas comunes de Caja, CajaMenor y Suministros de Access (cada una en su propia tabla). */
@MappedSuperclass
@Getter
@Setter
public abstract class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_transaccion", nullable = false)
    private String transactionId;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fondo_id", nullable = false)
    private Fund fund;

    @Column(name = "fecha", nullable = false)
    private LocalDate entryDate;

    @Column(name = "cedula", nullable = false)
    private String idNumber;

    @Column(name = "detalle")
    private String detail;

    @Column(name = "entradas", nullable = false)
    private BigDecimal inflow = BigDecimal.ZERO;

    @Column(name = "salidas", nullable = false)
    private BigDecimal outflow = BigDecimal.ZERO;

    @Column(name = "forma_pago")
    private String paymentMethod;

    @Column(name = "numero_cheque")
    private Integer checkNumber;

    @Column(name = "creado_en", nullable = false)
    private Instant createdAt;
}
