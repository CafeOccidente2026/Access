package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemissionLineRepository extends JpaRepository<RemissionLine, Long> {

    List<RemissionLine> findByRemissionId(Long remissionId);

    List<RemissionLine> findByRemissionAgencyIdAndRemissionRemissionDateBetween(
            Long agencyId, LocalDate from, LocalDate to);

    /** Salidas del Cod_Prod (Sld1/Sld2): SumaDeCantidad, SumaDeVr_Salida, SumaDeKXFrSal. Las lineas
     *  anteriores a V32 no tienen factor de salida: se usa el de su entrada origen, con el que se
     *  imprimieron. */
    @Query("SELECT new com.cafeoccidente.backend.inventory.repository.CodeTotals("
            + "SUM(l.quantity), SUM(l.outputValue), "
            + "SUM(l.quantity * COALESCE(l.exitPercentage, l.inventoryMovement.healthyPercentage, 0))) "
            + "FROM RemissionLine l WHERE l.remission.agency.id = :agencyId "
            + "AND l.inventoryMovement.productCode.id = :productCodeId "
            + "AND l.remission.remissionDate BETWEEN :from AND :to")
    CodeTotals sumExits(@Param("agencyId") Long agencyId, @Param("productCodeId") Long productCodeId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
