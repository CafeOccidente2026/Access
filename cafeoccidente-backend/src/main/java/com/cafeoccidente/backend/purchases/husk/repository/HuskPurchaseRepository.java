package com.cafeoccidente.backend.purchases.husk.repository;

import com.cafeoccidente.backend.purchases.husk.entity.HuskPurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HuskPurchaseRepository extends JpaRepository<HuskPurchase, Long> {

    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.husk.repository.MonthlyGrowerTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM HuskPurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate BETWEEN :monthStart AND :monthEnd
            """)
    MonthlyGrowerTotals sumMonthlyTotalsByIdNumber(
            @Param("idNumber") String idNumber,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /** Ultima factura ya usada en este modulo ("Para asignar # factura pasilla"). Null si no hay ninguna. */
    @Query("SELECT MAX(p.invoiceNumber) FROM HuskPurchase p")
    Integer findMaxInvoiceNumber();
}
