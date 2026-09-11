package com.cafeoccidente.backend.purchases.drycoffee.repository;

import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DryCoffeePurchaseRepository extends JpaRepository<DryCoffeePurchase, Long> {

    /**
     * Suma el Vr_Bruto y la Retefuente de todas las compras ya registradas para una cedula dentro
     * del rango de fechas dado. Se llama antes de guardar la compra en curso, asi que esta
     * naturalmente excluida (todavia no tiene fila). COALESCE devuelve 0 cuando no hay compras.
     */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.drycoffee.repository.MonthlyGrowerTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM DryCoffeePurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate BETWEEN :monthStart AND :monthEnd
            """)
    MonthlyGrowerTotals sumMonthlyTotalsByIdNumber(
            @Param("idNumber") String idNumber,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /** Ultima factura ya usada, para reservar la siguiente (paso "Fondo"). Null si no hay ninguna. */
    @Query("SELECT MAX(p.invoiceNumber) FROM DryCoffeePurchase p")
    Integer findMaxInvoiceNumber();
}
