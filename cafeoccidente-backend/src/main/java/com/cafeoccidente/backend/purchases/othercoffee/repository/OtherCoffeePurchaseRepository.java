package com.cafeoccidente.backend.purchases.othercoffee.repository;

import com.cafeoccidente.backend.purchases.othercoffee.entity.OtherCoffeePurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtherCoffeePurchaseRepository extends JpaRepository<OtherCoffeePurchase, Long> {

    /** Acumulado mensual PROPIO de este modulo (ReteMesCursoEsp, independiente del de Cafe Seco). */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.othercoffee.repository.MonthlyGrowerTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM OtherCoffeePurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate BETWEEN :monthStart AND :monthEnd
            """)
    MonthlyGrowerTotals sumMonthlyTotalsByIdNumber(
            @Param("idNumber") String idNumber,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /** Ver PurchaseInvoiceNumberService - la resolucion DIAN es por agencia, no por modulo. */
    @Query("SELECT MAX(p.invoiceNumber) FROM OtherCoffeePurchase p WHERE p.agency.id = :agencyId")
    Integer findMaxInvoiceNumber(@Param("agencyId") Long agencyId);
}
