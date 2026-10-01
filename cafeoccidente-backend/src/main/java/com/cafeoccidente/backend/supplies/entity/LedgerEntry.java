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

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column
    private String detail;

    @Column(nullable = false)
    private BigDecimal inflow = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal outflow = BigDecimal.ZERO;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "check_number")
    private Integer checkNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
