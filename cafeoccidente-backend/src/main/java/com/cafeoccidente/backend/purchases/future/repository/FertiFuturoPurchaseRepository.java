package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.FertiFuturoPurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FertiFuturoPurchaseRepository extends JpaRepository<FertiFuturoPurchase, Long> {

    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.future.repository.FertiFuturoMonthlyTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM FertiFuturoPurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate BETWEEN :monthStart AND :monthEnd
            """)
    FertiFuturoMonthlyTotals sumMonthlyTotalsByIdNumber(
            @Param("idNumber") String idNumber,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /** Ver PurchaseInvoiceNumberService - la resolucion DIAN es por agencia, no por modulo. */
    @Query("SELECT MAX(p.invoiceNumber) FROM FertiFuturoPurchase p WHERE p.agency.id = :agencyId")
    Integer findMaxInvoiceNumber(@Param("agencyId") Long agencyId);
}
