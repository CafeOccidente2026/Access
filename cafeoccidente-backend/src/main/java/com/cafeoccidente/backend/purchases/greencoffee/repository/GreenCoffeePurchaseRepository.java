package com.cafeoccidente.backend.purchases.greencoffee.repository;

import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GreenCoffeePurchaseRepository extends JpaRepository<GreenCoffeePurchase, Long> {

    /** Ver DryCoffeePurchaseRepository.sumDailyTotalsByIdNumber - mismo mecanismo, propio de VERDES. */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.greencoffee.repository.MonthlyGrowerTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM GreenCoffeePurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate = :purchaseDate
            """)
    MonthlyGrowerTotals sumDailyTotalsByIdNumber(
            @Param("idNumber") String idNumber, @Param("purchaseDate") LocalDate purchaseDate);

}
