package com.cafeoccidente.backend.purchases.greencoffee.repository;

import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GreenCoffeePurchaseRepository extends JpaRepository<GreenCoffeePurchase, Long> {

    /** Ver DryCoffeePurchaseRepository.sumMonthlyTotalsByIdNumber - mismo mecanismo, propio de VERDES. */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.greencoffee.repository.MonthlyGrowerTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM GreenCoffeePurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate BETWEEN :monthStart AND :monthEnd
            """)
    MonthlyGrowerTotals sumMonthlyTotalsByIdNumber(
            @Param("idNumber") String idNumber,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /**
     * Ultima factura ya usada en este modulo por esta agencia ("Para asignar # factura verdes").
     * Null si no hay ninguna. Ver DryCoffeePurchaseRepository.findMaxInvoiceNumber - mismo motivo
     * para filtrar por agencia.
     */
    @Query("SELECT MAX(p.invoiceNumber) FROM GreenCoffeePurchase p WHERE p.agency.id = :agencyId")
    Integer findMaxInvoiceNumber(@Param("agencyId") Long agencyId);
}
